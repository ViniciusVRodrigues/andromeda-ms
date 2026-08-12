package andromeda.sei.ms.feature.github.application.usecase;

import andromeda.sei.ms.feature.github.application.dto.AvailableGithubRepoOutput;
import andromeda.sei.ms.feature.github.domain.GithubConnection;
import andromeda.sei.ms.feature.github.domain.GithubConnectionRepository;
import andromeda.sei.ms.feature.github.domain.GithubRepo;
import andromeda.sei.ms.feature.github.domain.GithubRepoBinding;
import andromeda.sei.ms.feature.github.domain.GithubRepoBindingRepository;
import andromeda.sei.ms.feature.github.domain.GithubRepoRepository;
import andromeda.sei.ms.feature.github.domain.exception.GithubConnectionNotFoundException;
import andromeda.sei.ms.feature.github.infrastructure.client.GhRepoDto;
import andromeda.sei.ms.feature.github.infrastructure.client.GithubApiClient;
import andromeda.sei.ms.feature.integration.domain.Integration;
import andromeda.sei.ms.feature.integration.domain.IntegrationSyncLog;
import andromeda.sei.ms.feature.integration.domain.IntegrationSyncLogRepository;
import andromeda.sei.ms.feature.integration.domain.SyncOperation;
import andromeda.sei.ms.feature.integration.domain.SyncStatus;
import andromeda.sei.ms.feature.integration.domain.SyncTrigger;
import andromeda.sei.ms.shared.infrastructure.security.EncryptionService;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Union of repos visible to the configured PAT (GitHub REST {@code GET /user/repos}, page-capped)
 * and repos cached from previous syncs, de-duplicated by {@code fullName} (LIVE wins), with the
 * same exclusivity filtering as the Azure projects listing (design.md D5): fullNames bound to
 * other projects are dropped, the one bound to {@code forProjectId} is kept with
 * {@code boundToProjectId} populated. Permission {@code GITHUB_CONFIGURE} enforced at controller.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ListAvailableGithubReposUseCase {

    /** Bounded page cap for the live listing — a UI affordance, not a sync (design.md D5). */
    private static final int MAX_PAGES = 5;

    private final GithubConnectionRepository githubConnectionRepository;
    private final GithubRepoRepository githubRepoRepository;
    private final GithubRepoBindingRepository githubRepoBindingRepository;
    private final GithubApiClient githubApiClient;
    private final EncryptionService encryptionService;
    private final IntegrationSyncLogRepository syncLogRepository;

    public List<AvailableGithubRepoOutput> execute(String forProjectId) {
        String orgId = TenantContextHolder.get();
        GithubConnection connection = githubConnectionRepository
                .findByOrganizationIdAndActiveTrue(orgId)
                .orElseThrow(() -> new GithubConnectionNotFoundException(orgId));

        Instant startedAt = Instant.now();
        String authHeader = "Bearer " + encryptionService.decrypt(connection.getPersonalAccessToken());

        try {
            // LIVE + CACHED union, de-duplicated by fullName — LIVE wins (insertion order kept).
            Map<String, AvailableGithubRepoOutput> byFullName = new LinkedHashMap<>();
            for (GhRepoDto dto : githubApiClient.getUserRepos(authHeader, MAX_PAGES)) {
                if (dto.fullName() == null) continue;
                byFullName.putIfAbsent(dto.fullName(), new AvailableGithubRepoOutput(
                        dto.fullName(), dto.name(), dto.fullName(),
                        AvailableGithubRepoOutput.Source.LIVE, null));
            }
            for (GithubRepo repo : githubRepoRepository.findAllByOrganizationId(orgId)) {
                if (repo.getFullName() == null) continue;
                byFullName.putIfAbsent(repo.getFullName(), new AvailableGithubRepoOutput(
                        repo.getFullName(), repo.getName(), repo.getFullName(),
                        AvailableGithubRepoOutput.Source.CACHED, null));
            }

            Map<String, String> boundProjectByFullName = githubRepoBindingRepository
                    .findAllByOrganizationIdAndActiveTrue(orgId)
                    .stream()
                    .collect(Collectors.toMap(
                            GithubRepoBinding::getRepoId,
                            GithubRepoBinding::getProjectId,
                            (a, b) -> a));

            List<AvailableGithubRepoOutput> repos = byFullName.values().stream()
                    .map(item -> {
                        String boundToProjectId = boundProjectByFullName.get(item.fullName());
                        if (boundToProjectId != null && !boundToProjectId.equals(forProjectId)) {
                            // Bound to a DIFFERENT project (or bound at all when forProjectId is
                            // absent) — never offered for binding elsewhere.
                            return null;
                        }
                        return new AvailableGithubRepoOutput(
                                item.repoId(), item.name(), item.fullName(), item.source(), boundToProjectId);
                    })
                    .filter(Objects::nonNull)
                    .toList();

            log.info("GitHub available repos listed: org={} count={}", orgId, repos.size());

            writeSyncLog(orgId, startedAt, SyncStatus.SUCCESS, repos.size(), null, null, null, null);
            return repos;

        } catch (WebClientResponseException e) {
            // Failures of the live call are logged FAILED and rethrown — a dead PAT must stay
            // visible, cached-only fallback is deliberately NOT added (design.md D5).
            String body = e.getResponseBodyAsString();
            String detail = body.length() > 2000 ? body.substring(0, 2000) : body;
            log.error("GitHub list repos failed: org={} httpStatus={} body={}",
                    orgId, e.getStatusCode().value(), detail);
            writeSyncLog(orgId, startedAt, SyncStatus.FAILED, 0,
                    "GITHUB_LIST_REPOS_FAILED", e.getMessage(), e.getStatusCode().value(), detail);
            throw e;
        } catch (Exception e) {
            log.error("GitHub list repos failed: org={}", orgId, e);
            writeSyncLog(orgId, startedAt, SyncStatus.FAILED, 0,
                    "GITHUB_LIST_REPOS_FAILED", e.getMessage(), null, null);
            throw e;
        }
    }

    private void writeSyncLog(String orgId, Instant startedAt, SyncStatus status, int count,
                               String errorCode, String errorMessage, Integer httpStatus, String errorDetail) {
        Instant finishedAt = Instant.now();
        IntegrationSyncLog entry = new IntegrationSyncLog(
                null, orgId, Integration.GITHUB, SyncOperation.LIST_REPOS,
                status, SyncTrigger.MANUAL, null, startedAt, finishedAt,
                finishedAt.toEpochMilli() - startedAt.toEpochMilli(), count, 0, errorCode, errorMessage);
        entry.setHttpStatus(httpStatus);
        entry.setErrorDetail(errorDetail);
        syncLogRepository.save(entry);
    }
}
