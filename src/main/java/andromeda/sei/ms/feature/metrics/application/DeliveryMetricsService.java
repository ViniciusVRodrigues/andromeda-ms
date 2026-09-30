package andromeda.sei.ms.feature.metrics.application;

import andromeda.sei.ms.feature.metrics.domain.CompletedWorkItem;
import andromeda.sei.ms.feature.metrics.domain.DeliveryMetrics;
import andromeda.sei.ms.feature.metrics.domain.DeliveryMetricsCalculator;
import andromeda.sei.ms.feature.metrics.domain.MergedChange;
import andromeda.sei.ms.feature.organization.domain.Organization;
import andromeda.sei.ms.feature.organization.domain.OrganizationRepository;
import andromeda.sei.ms.feature.organization.domain.RemediationRules;
import andromeda.sei.ms.feature.vcs.application.port.VcsReadPort;
import andromeda.sei.ms.feature.vcs.domain.VcsPullRequest;
import andromeda.sei.ms.feature.vcs.domain.VcsRepository;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.domain.WorkItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Loads what {@link DeliveryMetricsCalculator} needs from local data only — no external call on
 * the request path (design D1): merged pull requests of a repository set in a window, with their
 * repository's default branch and whether the linked work item is a BUG, plus the organization's
 * remediation rules.
 *
 * <p>Every query is tenant-scoped. Scopes wider than a project (organization level) are built by
 * the caller as the union of project data via {@link #union}, never as an average of project values.
 */
@Component
@RequiredArgsConstructor
public class DeliveryMetricsService {

    private final VcsReadPort vcsReadPort;
    private final WorkItemRepository workItemRepository;
    private final OrganizationRepository organizationRepository;

    public RemediationRules remediationRules(String organizationId) {
        return organizationRepository.findById(organizationId)
                .map(Organization::getMetricSettings)
                .map(settings -> settings.remediationRules())
                .orElseGet(RemediationRules::defaults);
    }

    public List<MergedChange> mergedChangesOfProject(String organizationId, String projectId,
                                                     Instant from, Instant to) {
        return mergedChanges(organizationId, vcsReadPort.repositoriesOfProject(organizationId, projectId), from, to);
    }

    public List<MergedChange> mergedChanges(String organizationId, List<VcsRepository> repos, Instant from, Instant to) {
        if (repos == null || repos.isEmpty()) return List.of();
        Map<String, VcsRepository> repoById = repos.stream()
                .collect(Collectors.toMap(VcsRepository::getId, Function.identity(), (a, b) -> a));
        List<VcsPullRequest> merged = vcsReadPort.mergedPullRequests(
                organizationId, List.copyOf(repoById.keySet()), from, to);
        return toChanges(organizationId, repoById, merged);
    }

    /** Maps already-loaded pull requests; only merged ones become changes. */
    public List<MergedChange> mergedChangesFrom(String organizationId, List<VcsRepository> repos,
                                                List<VcsPullRequest> pullRequests) {
        if (repos == null || repos.isEmpty() || pullRequests == null) return List.of();
        Map<String, VcsRepository> repoById = repos.stream()
                .collect(Collectors.toMap(VcsRepository::getId, Function.identity(), (a, b) -> a));
        return toChanges(organizationId, repoById, pullRequests);
    }

    private List<MergedChange> toChanges(String organizationId, Map<String, VcsRepository> repoById,
                                         List<VcsPullRequest> pullRequests) {
        List<VcsPullRequest> merged = pullRequests.stream()
                .filter(pr -> pr.getMergedAt() != null && pr.getId() != null && pr.isActive())
                .toList();
        Set<String> bugExternalIds = bugExternalIds(organizationId, merged);
        return merged.stream()
                .map(pr -> toChange(pr, repoById.get(pr.getRepositoryId()), bugExternalIds))
                .toList();
    }

    /** Completed (DONE) work items with a completion date, for the Boards fallback. */
    public static List<CompletedWorkItem> completedItems(Collection<WorkItem> items) {
        if (items == null) return List.of();
        return items.stream()
                .filter(wi -> wi.getId() != null && wi.getCompletedAt() != null)
                .filter(wi -> wi.getResolvedStatus() == WorkItem.ResolvedStatus.DONE)
                .map(wi -> new CompletedWorkItem(wi.getId(), wi.getResolvedType() == WorkItem.ResolvedType.BUG,
                        wi.getCompletedAt(), wi.getLeadTimeHours()))
                .toList();
    }

    public DeliveryMetrics calculate(String organizationId, Collection<MergedChange> changes,
                                     Collection<CompletedWorkItem> completedItems, Instant from, Instant to) {
        return DeliveryMetricsCalculator.calculate(changes, completedItems, remediationRules(organizationId), from, to);
    }

    /** De-duplicated by id: a repository bound to two projects must not count its merges twice. */
    public static <T> List<T> union(Collection<? extends Collection<T>> parts, Function<T, String> idOf) {
        Map<String, T> byId = new LinkedHashMap<>();
        parts.forEach(part -> part.forEach(item -> byId.putIfAbsent(idOf.apply(item), item)));
        return List.copyOf(byId.values());
    }

    private Set<String> bugExternalIds(String organizationId, List<VcsPullRequest> prs) {
        List<String> linked = prs.stream()
                .map(VcsPullRequest::getLinkedWorkItemExternalId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (linked.isEmpty()) return Set.of();
        return workItemRepository
                .findAllByOrganizationIdAndExternalIdInAndSource(organizationId, linked, WorkItem.Source.AZURE_DEVOPS)
                .stream()
                .filter(WorkItem::isActive)
                .filter(wi -> wi.getResolvedType() == WorkItem.ResolvedType.BUG)
                .map(WorkItem::getExternalId)
                .collect(Collectors.toSet());
    }

    private MergedChange toChange(VcsPullRequest pr, VcsRepository repo, Set<String> bugExternalIds) {
        boolean linkedToBug = pr.getLinkedWorkItemExternalId() != null
                && bugExternalIds.contains(pr.getLinkedWorkItemExternalId());
        return new MergedChange(pr.getId(), pr.getTitle(), pr.getSourceBranch(), pr.getBaseBranch(),
                repo != null ? repo.getDefaultBranch() : null, linkedToBug,
                pr.getCreatedAt(), pr.getEarliestCommitAt(), pr.getMergedAt());
    }
}
