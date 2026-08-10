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
 * Watermark-based commit sync. Resolves {@code authorUserId} via {@code User.githubId} lookup,
 * leaving it {@code null} when unmatched while always preserving the raw {@code authorGithubLogin}
 * (spec: author resolution scenario).
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
        String authorUserId = resolveAuthorUserId(orgId, authorLogin);

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

    private String resolveAuthorUserId(String orgId, String authorLogin) {
        if (authorLogin == null) return null;
        return userRepository.findByOrganizationIdAndGithubId(orgId, authorLogin)
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
