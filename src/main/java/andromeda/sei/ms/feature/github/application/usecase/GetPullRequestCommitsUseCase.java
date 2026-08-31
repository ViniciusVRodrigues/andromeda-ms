package andromeda.sei.ms.feature.github.application.usecase;

import andromeda.sei.ms.feature.github.application.ProjectRepoResolver;
import andromeda.sei.ms.feature.github.application.dto.PullRequestCommitOutput;
import andromeda.sei.ms.feature.github.domain.GithubConnection;
import andromeda.sei.ms.feature.github.domain.GithubConnectionRepository;
import andromeda.sei.ms.feature.github.domain.GithubPullRequest;
import andromeda.sei.ms.feature.github.domain.GithubPullRequestRepository;
import andromeda.sei.ms.feature.github.domain.GithubRepo;
import andromeda.sei.ms.feature.github.domain.exception.GithubConnectionNotFoundException;
import andromeda.sei.ms.feature.github.domain.exception.GithubRepoNotFoundException;
import andromeda.sei.ms.feature.github.domain.exception.PullRequestCommitsUnavailableException;
import andromeda.sei.ms.feature.github.infrastructure.client.GithubApiClient;
import andromeda.sei.ms.shared.infrastructure.security.EncryptionService;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Commits of one pull request, fetched from GitHub at request time.
 *
 * <p>See {@code GithubApiClient#getPullRequestCommits} for why this cannot come from synced
 * data. The call reuses the client's response cache and rate-limit handling, so reopening the
 * same pull request does not spend a fresh call.
 */
@Component
@RequiredArgsConstructor
public class GetPullRequestCommitsUseCase {

    private final ProjectRepoResolver projectRepoResolver;
    private final GithubPullRequestRepository githubPullRequestRepository;
    private final GithubConnectionRepository githubConnectionRepository;
    private final GithubApiClient githubApiClient;
    private final EncryptionService encryptionService;

    public List<PullRequestCommitOutput> execute(String repoId, String pullRequestId) {
        String orgId = TenantContextHolder.get();

        GithubRepo repo = projectRepoResolver.resolveOne(orgId, repoId)
                .orElseThrow(() -> new GithubRepoNotFoundException(repoId));

        GithubPullRequest pr = githubPullRequestRepository
                .findByIdAndOrganizationId(pullRequestId, orgId)
                .filter(candidate -> repoId.equals(candidate.getRepoId()))
                .orElseThrow(() -> new GithubRepoNotFoundException(pullRequestId));

        GithubConnection connection = githubConnectionRepository
                .findByOrganizationIdAndActiveTrue(orgId)
                .orElseThrow(() -> new GithubConnectionNotFoundException(orgId));

        String authHeader = "Bearer " + encryptionService.decrypt(connection.getPersonalAccessToken());

        try {
            return githubApiClient
                    .getPullRequestCommits(repo.getOwner(), repo.getName(), pr.getNumber(), authHeader)
                    .stream()
                    .map(dto -> new PullRequestCommitOutput(
                            dto.sha(),
                            dto.commit() != null ? dto.commit().message() : null,
                            dto.author() != null ? dto.author().login() : null,
                            parseInstant(dto.commit() != null && dto.commit().author() != null
                                    ? dto.commit().author().date() : null)))
                    .toList();
        } catch (Exception e) {
            // The rest of the pull request detail comes from synced data and stays valid, so an
            // expired token or an exhausted rate limit must not take the whole panel down with
            // it. The caller reports the commit list as unavailable.
            throw new PullRequestCommitsUnavailableException(pr.getNumber(), e);
        }
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
