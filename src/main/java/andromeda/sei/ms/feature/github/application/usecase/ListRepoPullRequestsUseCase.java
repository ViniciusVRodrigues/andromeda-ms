package andromeda.sei.ms.feature.github.application.usecase;

import andromeda.sei.ms.feature.github.application.ProjectRepoResolver;
import andromeda.sei.ms.feature.github.application.dto.RepoPullRequestOutput;
import andromeda.sei.ms.feature.github.application.dto.RepoPullRequestPageOutput;
import andromeda.sei.ms.feature.github.domain.GithubPullRequest;
import andromeda.sei.ms.feature.github.domain.GithubPullRequestPage;
import andromeda.sei.ms.feature.github.domain.GithubPullRequestRepository;
import andromeda.sei.ms.feature.github.domain.PullRequestStatus;
import andromeda.sei.ms.feature.github.domain.exception.GithubRepoNotFoundException;
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
 * One page of a repository's pull requests, newest first.
 *
 * <p>Work item links are resolved here rather than at sync time: the pull request stores the
 * work item's {@code externalId}, so a reference whose work item had not been synced yet
 * starts resolving on its own once it is, with no re-sync of GitHub data. A stored id that
 * still matches nothing is returned unresolved and the row renders as an ordinary pull request.
 */
@Component
@RequiredArgsConstructor
public class ListRepoPullRequestsUseCase {

    private final ProjectRepoResolver projectRepoResolver;
    private final GithubPullRequestRepository githubPullRequestRepository;
    private final WorkItemRepository workItemRepository;

    public RepoPullRequestPageOutput execute(String repoId, PullRequestStatus status,
                                              String authorGithubLogin, int page, int size) {
        String orgId = TenantContextHolder.get();

        // Tenant check: a repo of another organization must not leak its pull requests.
        projectRepoResolver.resolveOne(orgId, repoId)
                .orElseThrow(() -> new GithubRepoNotFoundException(repoId));

        GithubPullRequestPage result = githubPullRequestRepository
                .findPageByRepo(orgId, repoId, status, authorGithubLogin, page, size);

        Map<String, WorkItem> workItemsByExternalId = resolveWorkItems(orgId, result.content());

        List<RepoPullRequestOutput> content = result.content().stream()
                .map(pr -> toOutput(pr, workItemsByExternalId))
                .toList();

        return new RepoPullRequestPageOutput(content, result.page(), result.size(),
                result.totalElements());
    }

    /** Resolves every referenced work item in one pass, rather than one lookup per row. */
    private Map<String, WorkItem> resolveWorkItems(String orgId, List<GithubPullRequest> prs) {
        List<String> externalIds = prs.stream()
                .map(GithubPullRequest::getLinkedWorkItemExternalId)
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .toList();

        if (externalIds.isEmpty()) return Map.of();

        return workItemRepository
                .findAllByOrganizationIdAndExternalIdInAndSource(orgId, externalIds,
                        WorkItem.Source.AZURE_DEVOPS)
                .stream()
                .collect(Collectors.toMap(WorkItem::getExternalId, Function.identity(),
                        (first, second) -> first));
    }

    private RepoPullRequestOutput toOutput(GithubPullRequest pr, Map<String, WorkItem> workItems) {
        String externalId = pr.getLinkedWorkItemExternalId();
        Optional<WorkItem> linked = externalId == null ? Optional.empty()
                : Optional.ofNullable(workItems.get(externalId));

        return new RepoPullRequestOutput(
                pr.getId(), pr.getNumber(), pr.getTitle(), pr.getStatus(),
                pr.getAuthorGithubLogin(), pr.getAuthorUserId(),
                pr.getCreatedAt(), pr.getMergedAt(), pr.getClosedAt(),
                pr.getCycleTimeHours(), pr.getTimeToFirstReviewHours(),
                linked.isPresent() ? externalId : null,
                linked.map(WorkItem::getId).orElse(null),
                linked.map(WorkItem::getTitle).orElse(null));
    }
}
