package andromeda.sei.ms.feature.github.application.usecase;

import andromeda.sei.ms.feature.github.domain.GithubCommit;
import andromeda.sei.ms.feature.github.domain.GithubCommitRepository;
import andromeda.sei.ms.feature.github.domain.GithubConnection;
import andromeda.sei.ms.feature.github.domain.GithubRepo;
import andromeda.sei.ms.feature.github.infrastructure.client.GhCommitDto;
import andromeda.sei.ms.feature.github.infrastructure.client.GithubApiClient;
import andromeda.sei.ms.feature.user.domain.UserRepository;
import andromeda.sei.ms.shared.infrastructure.security.EncryptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Watermark-based commit sync. Resolves {@code authorUserId} by matching {@code User.githubId}
 * against the author's numeric GitHub id (the value {@code GitHubOAuth2SuccessHandler} stores in
 * {@code githubId}), leaving it {@code null} when the commit has no top-level {@code author} or
 * no user matches, while always preserving the raw {@code authorGithubLogin}
 * (spec: author resolution scenario, BUG 2 fix — design.md D2).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SyncGithubCommitsUseCase {

    private final GithubCommitRepository githubCommitRepository;
    private final GithubApiClient githubApiClient;
    private final EncryptionService encryptionService;
    private final UserRepository userRepository;

    public int execute(GithubConnection connection, GithubRepo repo, Instant lastSyncAt) {
        String authHeader = "Bearer " + encryptionService.decrypt(connection.getPersonalAccessToken());
        String orgId = connection.getOrganizationId();

        var commits = githubApiClient.getCommits(repo.getOwner(), repo.getName(), authHeader, lastSyncAt);

        int processed = 0;
        for (GhCommitDto dto : commits) {
            try {
                upsertCommit(orgId, repo.getId(), dto);
                processed++;
            } catch (Exception e) {
                log.warn("Failed to upsert commit sha={} repo={}: {}", dto.sha(), repo.getFullName(), e.getMessage());
            }
        }
        return processed;
    }

    private void upsertCommit(String orgId, String repoId, GhCommitDto dto) {
        String authorLogin = dto.author() != null ? dto.author().login() : null;
        Long authorGithubNumericId = dto.author() != null ? dto.author().id() : null;
        String authorUserId = resolveAuthorUserId(orgId, authorGithubNumericId);

        String message = dto.commit() != null ? dto.commit().message() : null;
        Instant authoredAt = dto.commit() != null && dto.commit().author() != null
                ? parseInstant(dto.commit().author().date())
                : null;

        var existing = githubCommitRepository.findByOrganizationIdAndRepoIdAndSha(orgId, repoId, dto.sha());
        Instant now = Instant.now();

        GithubCommit commit = existing.orElseGet(() -> new GithubCommit(
                null, orgId, repoId, dto.sha(), message, authorLogin, authorUserId, authoredAt,
                true, now, now, null));

        commit.setMessage(message);
        commit.setAuthorGithubLogin(authorLogin);
        commit.setAuthorUserId(authorUserId);
        commit.setAuthoredAt(authoredAt);

        githubCommitRepository.save(commit);
    }

    private String resolveAuthorUserId(String orgId, Long authorGithubNumericId) {
        if (authorGithubNumericId == null) return null;
        return userRepository.findByOrganizationIdAndGithubId(orgId, String.valueOf(authorGithubNumericId))
                .map(u -> u.getId())
                .orElse(null);
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
