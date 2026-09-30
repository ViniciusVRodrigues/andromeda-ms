package andromeda.sei.ms.feature.vcs.application.usecase;

import andromeda.sei.ms.feature.vcs.application.dto.PullRequestCommitOutput;
import andromeda.sei.ms.feature.vcs.application.port.VcsCredentials;
import andromeda.sei.ms.feature.vcs.application.port.VcsProviderRegistry;
import andromeda.sei.ms.feature.vcs.application.port.VcsReadPort;
import andromeda.sei.ms.feature.vcs.domain.VcsPullRequest;
import andromeda.sei.ms.feature.vcs.domain.VcsPullRequestRepository;
import andromeda.sei.ms.feature.vcs.domain.VcsRepository;
import andromeda.sei.ms.feature.vcs.domain.exception.PullRequestCommitsUnavailableException;
import andromeda.sei.ms.feature.vcs.domain.exception.RepoNotFoundException;
import andromeda.sei.ms.feature.vcs.domain.exception.VcsConnectionNotFoundException;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Commits of one pull request, fetched live through the repository provider's
 * {@code VcsProviderClient} (vcs-repository-read-api): synced commits carry no pull request
 * reference. A provider failure answers {@code 502 PR_COMMITS_UNAVAILABLE} without affecting the
 * rest of the pull request detail, which comes from synced data.
 */
@Component
@RequiredArgsConstructor
public class GetPullRequestCommitsUseCase {

    private final VcsReadPort vcsReadPort;
    private final VcsPullRequestRepository pullRequestRepository;
    private final VcsProviderRegistry providerRegistry;

    public List<PullRequestCommitOutput> execute(String repositoryId, String pullRequestId) {
        String orgId = TenantContextHolder.get();
        VcsRepository repo = vcsReadPort.repository(orgId, repositoryId)
                .orElseThrow(() -> new RepoNotFoundException(repositoryId));
        VcsPullRequest pr = pullRequestRepository.findByIdAndOrganizationId(pullRequestId, orgId)
                .filter(candidate -> repositoryId.equals(candidate.getRepositoryId()))
                .orElseThrow(() -> new RepoNotFoundException(pullRequestId));
        VcsCredentials credentials = providerRegistry.credentialsProvider(repo.getProvider())
                .credentials(orgId)
                .orElseThrow(() -> new VcsConnectionNotFoundException(repo.getProvider().name()));

        try {
            return providerRegistry.client(repo.getProvider()).pullRequestCommits(credentials, repo, pr).stream()
                    .map(c -> new PullRequestCommitOutput(c.sha(), c.message(), c.authorLogin(), c.authoredAt()))
                    .toList();
        } catch (RuntimeException e) {
            // An expired token or exhausted rate limit must not take the whole panel down.
            throw new PullRequestCommitsUnavailableException(pr.getNumber(), e);
        }
    }
}
