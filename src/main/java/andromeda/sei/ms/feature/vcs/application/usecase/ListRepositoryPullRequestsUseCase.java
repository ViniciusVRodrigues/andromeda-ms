package andromeda.sei.ms.feature.vcs.application.usecase;

import andromeda.sei.ms.feature.vcs.application.dto.RepositoryPullRequestOutput;
import andromeda.sei.ms.feature.vcs.application.dto.RepositoryPullRequestPageOutput;
import andromeda.sei.ms.feature.vcs.application.port.VcsReadPort;
import andromeda.sei.ms.feature.vcs.domain.PullRequestStatus;
import andromeda.sei.ms.feature.vcs.domain.VcsPullRequest;
import andromeda.sei.ms.feature.vcs.domain.VcsPullRequestPage;
import andromeda.sei.ms.feature.vcs.domain.VcsPullRequestRepository;
import andromeda.sei.ms.feature.vcs.domain.exception.RepoNotFoundException;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.domain.WorkItemRepository;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * One page of a repository's pull requests, newest first, optionally filtered by status and author
 * login.
 *
 * <p>Work item links are resolved here, at read time (vcs-work-item-linking): the pull request
 * stores the work item's external id even when the item was not synced yet, so a reference starts
 * resolving on its own once it is. A stored id that still matches nothing is returned unresolved.
 */
@Component
@RequiredArgsConstructor
public class ListRepositoryPullRequestsUseCase {

    private final VcsReadPort vcsReadPort;
    private final VcsPullRequestRepository pullRequestRepository;
    private final WorkItemRepository workItemRepository;

    public RepositoryPullRequestPageOutput execute(String repositoryId, PullRequestStatus status,
                                                  String authorLogin, int page, int size) {
        String orgId = TenantContextHolder.get();
        vcsReadPort.repository(orgId, repositoryId).orElseThrow(() -> new RepoNotFoundException(repositoryId));

        VcsPullRequestPage result = pullRequestRepository.findPageByRepository(
                orgId, repositoryId, status, authorLogin, page, size);
        Map<String, WorkItem> workItemsByExternalId = resolveWorkItems(orgId, result.content());

        List<RepositoryPullRequestOutput> content = result.content().stream()
                .map(pr -> toOutput(pr, workItemsByExternalId))
                .toList();
        return new RepositoryPullRequestPageOutput(content, result.page(), result.size(), result.totalElements());
    }

    /** Resolves every referenced work item in one pass, rather than one lookup per row. */
    private Map<String, WorkItem> resolveWorkItems(String orgId, List<VcsPullRequest> prs) {
        List<String> externalIds = prs.stream()
                .map(VcsPullRequest::getLinkedWorkItemExternalId)
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .toList();
        if (externalIds.isEmpty()) return Map.of();
        return workItemRepository
                .findAllByOrganizationIdAndExternalIdInAndSource(orgId, externalIds, WorkItem.Source.AZURE_DEVOPS)
                .stream()
                .collect(Collectors.toMap(WorkItem::getExternalId, Function.identity(), (first, second) -> first));
    }

    private RepositoryPullRequestOutput toOutput(VcsPullRequest pr, Map<String, WorkItem> workItems) {
        String externalId = pr.getLinkedWorkItemExternalId();
        Optional<WorkItem> linked = externalId == null ? Optional.empty() : Optional.ofNullable(workItems.get(externalId));
        return new RepositoryPullRequestOutput(
                pr.getId(), pr.getNumber() != null ? pr.getNumber() : 0, pr.getTitle(), pr.getStatus(),
                pr.getAuthorLogin(), pr.getAuthorUserId(),
                pr.getCreatedAt(), pr.getMergedAt(), pr.getClosedAt(),
                pr.getCycleTimeHours(), pr.getTimeToFirstReviewHours(),
                linked.isPresent() ? externalId : null,
                linked.map(WorkItem::getId).orElse(null),
                linked.map(WorkItem::getTitle).orElse(null),
                pr.isDraft(),
                pr.getTimeToFirstApprovalHours(),
                pr.getProvider());
    }
}
