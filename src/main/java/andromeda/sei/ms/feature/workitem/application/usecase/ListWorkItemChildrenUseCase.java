package andromeda.sei.ms.feature.workitem.application.usecase;

import andromeda.sei.ms.feature.azure.domain.AzureProjectBindingRepository;
import andromeda.sei.ms.feature.azure.domain.HierarchyLevel;
import andromeda.sei.ms.feature.workitem.application.classifier.BacklogOrdering;
import andromeda.sei.ms.feature.workitem.application.classifier.WorkItemHierarchyClassifier;
import andromeda.sei.ms.feature.workitem.application.classifier.WorkItemHierarchyClassifier.Classification;
import andromeda.sei.ms.feature.workitem.application.classifier.WorkItemHierarchyClassifier.HierarchyPlacement;
import andromeda.sei.ms.feature.workitem.domain.BacklogItemInfo;
import andromeda.sei.ms.feature.workitem.application.dto.WorkItemChildrenOutput;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.domain.WorkItemHierarchyMetrics;
import andromeda.sei.ms.feature.workitem.domain.WorkItemRepository;
import andromeda.sei.ms.feature.workitem.domain.exception.WorkItemNotFoundException;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Lists the children of a single work item, capped at a fixed soft limit, per
 * openspec/changes/project-backlog-view/design.md Decision 5.
 */
@Component
@RequiredArgsConstructor
public class ListWorkItemChildrenUseCase {

    /** Soft cap on children returned per parent; see design.md Decision 5 / Open Questions. */
    public static final int CHILDREN_SOFT_CAP = 500;

    private final WorkItemRepository workItemRepository;
    private final AzureProjectBindingRepository azureProjectBindingRepository;
    private final WorkItemHierarchyClassifier classifier;
    private final WorkItemHierarchyMetricsCalculator metricsCalculator;

    public WorkItemChildrenOutput execute(String workItemId) {
        String orgId = TenantContextHolder.get();

        WorkItem parent = workItemRepository.findById(workItemId)
                .filter(w -> w.getOrganizationId().equals(orgId) && w.isActive())
                .orElseThrow(() -> new WorkItemNotFoundException(workItemId));

        List<HierarchyLevel> hierarchyLevels = azureProjectBindingRepository
                .findByOrganizationIdAndProjectIdAndActiveTrue(orgId, parent.getProjectId())
                .map(b -> b.getWorkflowConfig() != null ? b.getWorkflowConfig().getHierarchyLevels() : List.<HierarchyLevel>of())
                .orElse(List.of());

        // The whole project is loaded once: it resolves every parent in memory (no query per
        // candidate), feeds the same classification the roots use, and feeds the rollups.
        List<WorkItem> projectItems = workItemRepository
                .findAllByOrganizationIdAndProjectId(orgId, parent.getProjectId())
                .stream()
                .filter(WorkItem::isActive)
                .toList();
        Map<String, WorkItem> byExternalId = new HashMap<>();
        for (WorkItem item : projectItems) {
            if (item.getExternalId() != null) byExternalId.putIfAbsent(item.getExternalId(), item);
        }
        Map<String, HierarchyPlacement> placements = classifier.classifyAll(projectItems, hierarchyLevels);
        Function<String, Integer> rankLookup = classifier.rankLookup(hierarchyLevels);
        Function<WorkItem, Integer> rankOf = item -> rankLookup.apply(item.getRawType());

        List<WorkItem> validChildren = projectItems.stream()
                .filter(item -> parent.getExternalId().equals(item.getParentExternalId()))
                .filter(item -> placements.get(item.getId()).classification() == Classification.CHILD)
                .sorted(BacklogOrdering.comparator(rankOf))
                .toList();

        boolean truncated = validChildren.size() > CHILDREN_SOFT_CAP;
        List<WorkItem> page = truncated ? validChildren.subList(0, CHILDREN_SOFT_CAP) : validChildren;

        // A returned child own rollup depends on descendants that are not in `page`, and its
        // hasChildren flag on grandchildren, hence the metrics of the whole project.
        Map<String, WorkItemHierarchyMetrics> allMetrics = metricsCalculator.calculate(projectItems, placements);
        Map<String, WorkItemHierarchyMetrics> metrics = new HashMap<>();
        Map<String, BacklogItemInfo> info = new HashMap<>();
        for (WorkItem child : page) {
            WorkItemHierarchyMetrics m = allMetrics.get(child.getId());
            if (m != null) metrics.put(child.getId(), m);
            info.put(child.getId(), ProjectBacklogClassificationService.infoOf(child, placements, byExternalId, rankOf));
        }
        return new WorkItemChildrenOutput(page, truncated, metrics, info);
    }
}
