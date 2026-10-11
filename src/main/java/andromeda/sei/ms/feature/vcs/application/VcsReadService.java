package andromeda.sei.ms.feature.vcs.application;

import andromeda.sei.ms.feature.vcs.application.port.VcsReadPort;
import andromeda.sei.ms.feature.vcs.domain.PullRequestStatus;
import andromeda.sei.ms.feature.vcs.domain.VcsPullRequest;
import andromeda.sei.ms.feature.vcs.domain.VcsPullRequestRepository;
import andromeda.sei.ms.feature.vcs.domain.VcsRepoBinding;
import andromeda.sei.ms.feature.vcs.domain.VcsRepoBindingRepository;
import andromeda.sei.ms.feature.vcs.domain.VcsRepository;
import andromeda.sei.ms.feature.vcs.domain.VcsRepositoryRepository;
import andromeda.sei.ms.feature.vcs.domain.VcsReviewEvent;
import andromeda.sei.ms.feature.vcs.domain.VcsReviewEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * {@link VcsReadPort} over the neutral collections. The project → repository resolution is by the
 * binding's internal {@code repositoryId} — the {@code owner/name} ↔ id bridge of the old
 * {@code ProjectRepoResolver} (and its two bugs) is gone.
 */
@Component
@RequiredArgsConstructor
public class VcsReadService implements VcsReadPort {

    private final VcsRepoBindingRepository bindingRepository;
    private final VcsRepositoryRepository repositoryRepository;
    private final VcsPullRequestRepository pullRequestRepository;
    private final VcsReviewEventRepository reviewEventRepository;

    @Override
    public List<VcsRepoBinding> bindingsOfProject(String organizationId, String projectId) {
        return bindingRepository.findAllActiveByOrganizationIdAndProjectId(organizationId, projectId);
    }

    @Override
    public List<VcsRepository> repositoriesOfProject(String organizationId, String projectId) {
        List<String> repositoryIds = bindingsOfProject(organizationId, projectId).stream()
                .map(VcsRepoBinding::getRepositoryId)
                .distinct()
                .toList();
        return repositoryRepository.findAllByOrganizationIdAndIdIn(organizationId, repositoryIds);
    }

    @Override
    public Optional<VcsRepository> repository(String organizationId, String repositoryId) {
        return repositoryRepository.findByIdAndOrganizationId(repositoryId, organizationId);
    }

    @Override
    public List<VcsPullRequest> pullRequestsOfRepository(String organizationId, String repositoryId) {
        return pullRequestRepository.findAllByOrganizationIdAndRepositoryId(organizationId, repositoryId).stream()
                .filter(VcsPullRequest::isActive)
                .toList();
    }

    @Override
    public List<VcsPullRequest> openPullRequests(String organizationId, Collection<String> repositoryIds) {
        return pullRequestRepository.findAllByOrganizationIdAndRepositoryIdInAndStatus(
                organizationId, repositoryIds, PullRequestStatus.OPEN);
    }

    @Override
    public List<VcsPullRequest> pullRequestsCreatedIn(String organizationId, Collection<String> repositoryIds,
                                                      Instant from, Instant to) {
        return pullRequestRepository.findAllByOrganizationIdAndRepositoryIdInAndCreatedAtBetween(
                organizationId, repositoryIds, from, to);
    }

    @Override
    public List<VcsPullRequest> mergedPullRequests(String organizationId, Collection<String> repositoryIds,
                                                   Instant from, Instant to) {
        return pullRequestRepository.findMergedByOrganizationIdAndRepositoryIdInAndMergedAtBetween(
                organizationId, repositoryIds, from, to);
    }

    @Override
    public List<VcsPullRequest> pullRequestsTouchedSince(String organizationId, Collection<String> repositoryIds,
                                                         Instant since) {
        return pullRequestRepository.findActiveTouchedSince(organizationId, repositoryIds, since);
    }

    @Override
    public List<VcsPullRequest> pullRequestsByAuthor(String organizationId, String userId, Instant from, Instant to) {
        return pullRequestRepository.findAllByOrganizationIdAndAuthorUserIdAndCreatedAtBetween(
                        organizationId, userId, from, to).stream()
                .filter(VcsPullRequest::isActive)
                .toList();
    }

    @Override
    public List<VcsReviewEvent> reviewEvents(String organizationId, Collection<String> pullRequestIds) {
        return reviewEventRepository.findAllByOrganizationIdAndPullRequestIdIn(organizationId, pullRequestIds);
    }

    @Override
    public List<VcsPullRequest> pullRequestMetricFieldsTouchedSince(String organizationId, Collection<String> repositoryIds,
                                                                   Instant since) {
        return pullRequestRepository.findMetricFieldsTouchedSince(organizationId, repositoryIds, since);
    }
}
