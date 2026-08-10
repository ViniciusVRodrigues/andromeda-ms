package andromeda.sei.ms.feature.workitem.application.usecase;

import andromeda.sei.ms.feature.azure.domain.AzureProjectBindingRepository;
import andromeda.sei.ms.feature.azure.domain.HierarchyLevel;
import andromeda.sei.ms.feature.project.domain.ProjectRepository;
import andromeda.sei.ms.feature.project.domain.exception.ProjectNotFoundException;
import andromeda.sei.ms.feature.workitem.application.classifier.WorkItemHierarchyClassifier;
import andromeda.sei.ms.feature.workitem.application.classifier.WorkItemHierarchyClassifier.Classification;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.domain.WorkItemHierarchyMetrics;
import andromeda.sei.ms.feature.workitem.domain.WorkItemPageResult;
import andromeda.sei.ms.feature.workitem.domain.WorkItemRepository;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Classifies every active work item in a project's backlog and returns a coherent,
 * classification-scoped page of the result.
 *
 * <p>Root/orphan/child classification depends on cross-item parent lookups within the same
 * project (see {@link WorkItemHierarchyClassifier}), so it cannot be expressed as a Mongo query
 * predicate and cannot be paginated before filtering — a page of "candidates" filtered afterward
 * would report a total that doesn't match what's actually returned, and would produce short or
 * empty pages once the classification is applied (see project-backlog-view root/orphan pagination
 * bug). Instead this loads every active work item in the project once, classifies all of them,
 * filters to the requested classification, and paginates that filtered list in memory — so
 * {@code totalElements} always matches {@code content} for the classification being served.
 *
 * <p>This is a full per-project scan. It's the pragmatic choice here: a project's synced work
 * item set is bounded by what exists in a single Azure DevOps project (this codebase already does
 * unbounded per-project scans for the same collection, e.g. {@code ForceResyncProjectUseCase} and
 * {@code CalculateWorkItemMetricsUseCase}), and avoids a second class of query (rank-aware
 * filtering) that Mongo can't express directly. If backlog sizes grow enough to make this
 * expensive, the next step is caching the classified set per (org, project) with invalidation on
 * sync completion, rather than trying to paginate-then-filter again.
 */
@Component
@RequiredArgsConstructor
public class ProjectBacklogClassificationService {

    private final ProjectRepository projectRepository;
    private final AzureProjectBindingRepository azureProjectBindingRepository;
    private final WorkItemRepository workItemRepository;
    private final WorkItemHierarchyClassifier classifier;
    private final WorkItemHierarchyMetricsCalculator metricsCalculator;

    public WorkItemPageResult execute(String projectId, Classification classification, int page, int size) {
        String orgId = TenantContextHolder.get();

        var project = projectRepository.findById(projectId)
                .filter(p -> p.getOrganizationId().equals(orgId) && p.isActive())
                .orElseThrow(() -> new ProjectNotFoundException(projectId));

        List<HierarchyLevel> hierarchyLevels = resolveHierarchyLevels(orgId, project.getId());

        List<WorkItem> allItems = workItemRepository.findAllByOrganizationIdAndProjectId(orgId, project.getId())
                .stream()
                .filter(WorkItem::isActive)
                .toList();

        Map<String, WorkItem> byExternalId = allItems.stream()
                .collect(java.util.stream.Collectors.toMap(WorkItem::getExternalId, Function.identity(), (a, b) -> a));
        Function<String, WorkItem> resolver = byExternalId::get;

        List<WorkItem> matching = allItems.stream()
                .filter(item -> classifier.classify(item, hierarchyLevels, resolver) == classification)
                .toList();

        long totalElements = matching.size();
        int fromIndex = Math.min(page * size, matching.size());
        int toIndex = Math.min(fromIndex + size, matching.size());
        List<WorkItem> pageContent = matching.subList(fromIndex, toIndex);

        // Computed over the full project set (already in memory here) so rollups see every
        // descendant, not just the ones on this page; then narrowed to the page being returned.
        Map<String, WorkItemHierarchyMetrics> allMetrics =
                metricsCalculator.calculate(allItems, hierarchyLevels);
        Map<String, WorkItemHierarchyMetrics> pageMetrics = pageContent.stream()
                .filter(item -> allMetrics.containsKey(item.getId()))
                .collect(java.util.stream.Collectors.toMap(WorkItem::getId, item -> allMetrics.get(item.getId())));

        return new WorkItemPageResult(pageContent, page, size, totalElements, pageMetrics);
    }

    private List<HierarchyLevel> resolveHierarchyLevels(String orgId, String projectId) {
        return azureProjectBindingRepository
                .findByOrganizationIdAndProjectIdAndActiveTrue(orgId, projectId)
                .map(b -> b.getWorkflowConfig() != null ? b.getWorkflowConfig().getHierarchyLevels() : List.<HierarchyLevel>of())
                .orElse(List.of());
    }
}
