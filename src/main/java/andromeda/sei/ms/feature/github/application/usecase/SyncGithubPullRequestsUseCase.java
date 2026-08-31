package andromeda.sei.ms.feature.github.application.usecase;

import andromeda.sei.ms.feature.github.domain.GithubConnection;
import andromeda.sei.ms.feature.github.domain.GithubPullRequest;
import andromeda.sei.ms.feature.github.domain.GithubPullRequestRepository;
import andromeda.sei.ms.feature.github.domain.GithubRepo;
import andromeda.sei.ms.feature.github.domain.PullRequestStatus;
import andromeda.sei.ms.feature.github.infrastructure.client.GhPullRequestDto;
import andromeda.sei.ms.feature.github.infrastructure.client.GithubApiClient;
import andromeda.sei.ms.feature.github.application.WorkItemReferenceExtractor;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.domain.WorkItemRepository;
import andromeda.sei.ms.shared.infrastructure.security.EncryptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Watermark-based PR sync (upsert key {@code organizationId,repoId,externalId}); derives
 * {@code status} from GitHub's {@code state}/{@code merged_at}. Resolves {@code authorUserId}
 * via {@link GithubAuthorResolver} (provider account first, legacy {@code User.githubId}
 * fallback second, design.md D7).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SyncGithubPullRequestsUseCase {

    private final GithubPullRequestRepository githubPullRequestRepository;
    private final GithubApiClient githubApiClient;
    private final EncryptionService encryptionService;
    private final GithubAuthorResolver githubAuthorResolver;
    private final WorkItemRepository workItemRepository;

    public List<GithubPullRequest> execute(GithubConnection connection, GithubRepo repo, Instant lastSyncAt) {
        String authHeader = "Bearer " + encryptionService.decrypt(connection.getPersonalAccessToken());
        String orgId = connection.getOrganizationId();

        var prs = githubApiClient.getPullRequests(repo.getOwner(), repo.getName(), authHeader, lastSyncAt);

        return prs.stream()
                .map(dto -> upsertPullRequest(connection, repo.getId(), dto))
                .toList();
    }

    private GithubPullRequest upsertPullRequest(GithubConnection connection, String repoId, GhPullRequestDto dto) {
        String orgId = connection.getOrganizationId();
        String authorLogin = dto.user() != null ? dto.user().login() : null;
        Long authorGithubNumericId = dto.user() != null ? dto.user().id() : null;
        String authorUserId = githubAuthorResolver.resolve(orgId, authorGithubNumericId);

        Instant createdAt = parseInstant(dto.createdAt());
        Instant mergedAt = parseInstant(dto.mergedAt());
        Instant closedAt = parseInstant(dto.closedAt());
        Instant externalUpdatedAt = parseInstant(dto.updatedAt());
        PullRequestStatus status = resolveStatus(dto.state(), mergedAt);

        String externalId = String.valueOf(dto.id());
        var existing = githubPullRequestRepository.findByOrganizationIdAndRepoIdAndExternalId(
                orgId, repoId, externalId);

        GithubPullRequest pr = existing.orElseGet(() -> new GithubPullRequest(
                null, orgId, repoId, externalId, dto.number(), dto.title(), status,
                authorLogin, authorUserId, createdAt, mergedAt, closedAt, externalUpdatedAt,
                null, null, true, Instant.now(), null));

        pr.setNumber(dto.number());
        pr.setTitle(dto.title());
        pr.setStatus(status);
        pr.setAuthorGithubLogin(authorLogin);
        pr.setAuthorUserId(authorUserId);
        if (pr.getCreatedAt() == null) pr.setCreatedAt(createdAt);
        pr.setMergedAt(mergedAt);
        pr.setClosedAt(closedAt);
        pr.setExternalUpdatedAt(externalUpdatedAt);
        pr.setLinkedWorkItemExternalId(resolveWorkItemLink(connection, dto));

        return githubPullRequestRepository.save(pr);
    }

    /**
     * Best-effort work item reference for this pull request.
     *
     * <p>Stores the work item's {@code externalId} rather than an internal id: sync order
     * across integrations is not guaranteed, so a reference whose work item has not been
     * synced yet still resolves later, at read time, with no re-sync of GitHub data. The
     * candidate is only kept when a work item actually carries that id in this organization —
     * a wrong correlation is worse than none, since it misstates the very code-to-delivery
     * relationship the platform exists to measure.
     *
     * <p>Never fails the sync: linking is a bonus, and a pull request that could not be linked
     * is a normal, expected outcome for organizations whose naming conventions differ.
     */
    private String resolveWorkItemLink(GithubConnection connection, GhPullRequestDto dto) {
        try {
            String branch = dto.head() != null ? dto.head().ref() : null;
            Optional<String> candidate = WorkItemReferenceExtractor.extract(
                    connection.getWorkItemLinkConfig(), branch, dto.body(), dto.title());
            if (candidate.isEmpty()) return null;

            String externalId = candidate.get();
            boolean exists = workItemRepository.findByOrganizationIdAndExternalIdAndSource(
                    connection.getOrganizationId(), externalId, WorkItem.Source.AZURE_DEVOPS).isPresent();
            return exists ? externalId : null;
        } catch (Exception e) {
            log.warn("Work item link extraction failed for PR #{}: {}", dto.number(), e.getMessage());
            return null;
        }
    }

    private PullRequestStatus resolveStatus(String state, Instant mergedAt) {
        if (mergedAt != null) return PullRequestStatus.MERGED;
        if ("closed".equalsIgnoreCase(state)) return PullRequestStatus.CLOSED;
        return PullRequestStatus.OPEN;
    }

    private Instant parseInstant(String iso) {
        if (iso == null) return null;
        try {
            return Instant.parse(iso);
        } catch (Exception e) {
            return null;
        }
    }
}
