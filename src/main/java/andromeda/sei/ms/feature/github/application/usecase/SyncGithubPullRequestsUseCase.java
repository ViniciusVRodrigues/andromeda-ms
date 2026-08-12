package andromeda.sei.ms.feature.github.application.usecase;

import andromeda.sei.ms.feature.github.domain.GithubConnection;
import andromeda.sei.ms.feature.github.domain.GithubPullRequest;
import andromeda.sei.ms.feature.github.domain.GithubPullRequestRepository;
import andromeda.sei.ms.feature.github.domain.GithubRepo;
import andromeda.sei.ms.feature.github.domain.PullRequestStatus;
import andromeda.sei.ms.feature.github.infrastructure.client.GhPullRequestDto;
import andromeda.sei.ms.feature.github.infrastructure.client.GithubApiClient;
import andromeda.sei.ms.feature.user.domain.UserRepository;
import andromeda.sei.ms.shared.infrastructure.security.EncryptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Watermark-based PR sync (upsert key {@code organizationId,repoId,externalId}); derives
 * {@code status} from GitHub's {@code state}/{@code merged_at}. Resolves {@code authorUserId}
 * by matching {@code User.githubId} against the author's numeric GitHub id (BUG 2 fix,
 * design.md D2).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SyncGithubPullRequestsUseCase {

    private final GithubPullRequestRepository githubPullRequestRepository;
    private final GithubApiClient githubApiClient;
    private final EncryptionService encryptionService;
    private final UserRepository userRepository;

    public List<GithubPullRequest> execute(GithubConnection connection, GithubRepo repo, Instant lastSyncAt) {
        String authHeader = "Bearer " + encryptionService.decrypt(connection.getPersonalAccessToken());
        String orgId = connection.getOrganizationId();

        var prs = githubApiClient.getPullRequests(repo.getOwner(), repo.getName(), authHeader, lastSyncAt);

        return prs.stream()
                .map(dto -> upsertPullRequest(orgId, repo.getId(), dto))
                .toList();
    }

    private GithubPullRequest upsertPullRequest(String orgId, String repoId, GhPullRequestDto dto) {
        String authorLogin = dto.user() != null ? dto.user().login() : null;
        Long authorGithubNumericId = dto.user() != null ? dto.user().id() : null;
        String authorUserId = authorGithubNumericId != null
                ? userRepository.findByOrganizationIdAndGithubId(orgId, String.valueOf(authorGithubNumericId))
                        .map(u -> u.getId()).orElse(null)
                : null;

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

        return githubPullRequestRepository.save(pr);
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
