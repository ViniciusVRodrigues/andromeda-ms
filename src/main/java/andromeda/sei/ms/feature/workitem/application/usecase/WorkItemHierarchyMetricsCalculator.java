package andromeda.sei.ms.feature.workitem.application.usecase;

import andromeda.sei.ms.feature.azure.domain.HierarchyLevel;
import andromeda.sei.ms.feature.workitem.application.classifier.WorkItemHierarchyClassifier;
import andromeda.sei.ms.feature.workitem.application.classifier.WorkItemHierarchyClassifier.Classification;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.domain.WorkItemHierarchyMetrics;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Computes {@link WorkItemHierarchyMetrics} for a whole project's work item set in one pass.
 *
 * <p>Takes the already-loaded item list (callers such as
 * {@link ProjectBacklogClassificationService} load and classify the project once), so this adds
 * no queries — building a parent → children index in memory is what keeps "does this item have
 * children?" from becoming an N+1 lookup per row.
 *
 * <p>"Has children" means children that are VALID by the same rule the tree uses: a child whose
 * parent link resolves but violates the declared hierarchy rank is an orphan, and is surfaced in
 * the orphans bucket rather than under this parent. Counting it here would render an expand
 * toggle that opens an empty list, so the classifier is reused rather than reimplemented.
 */
@Component
@RequiredArgsConstructor
public class WorkItemHierarchyMetricsCalculator {

    private final WorkItemHierarchyClassifier classifier;

    /**
     * @param items every active work item in the project (the classification scope)
     * @return metrics keyed by work item id; items with no descendants map to
     *         {@link WorkItemHierarchyMetrics#EMPTY}
     */
    public Map<String, WorkItemHierarchyMetrics> calculate(List<WorkItem> items,
                                                            List<HierarchyLevel> hierarchyLevels) {
        Map<String, WorkItem> byExternalId = new HashMap<>();
        for (WorkItem item : items) {
            if (item.getExternalId() != null) byExternalId.putIfAbsent(item.getExternalId(), item);
        }
        Function<String, WorkItem> resolver = byExternalId::get;

        // parentExternalId -> valid children, using the tree's own notion of a valid child.
        Map<String, List<WorkItem>> childrenByParent = new HashMap<>();
        for (WorkItem item : items) {
            String parentExternalId = item.getParentExternalId();
            if (parentExternalId == null) continue;
            if (classifier.classify(item, hierarchyLevels, resolver) != Classification.CHILD) continue;
            childrenByParent.computeIfAbsent(parentExternalId, k -> new ArrayList<>()).add(item);
        }

        Map<String, WorkItemHierarchyMetrics> result = new HashMap<>();
        for (WorkItem item : items) {
            result.put(item.getId(), computeFor(item, childrenByParent));
        }
        return result;
    }

    private WorkItemHierarchyMetrics computeFor(WorkItem item, Map<String, List<WorkItem>> childrenByParent) {
        List<WorkItem> directChildren = childrenByParent.getOrDefault(item.getExternalId(), List.of());
        if (directChildren.isEmpty()) return WorkItemHierarchyMetrics.EMPTY;

        // Malformed Azure data can contain a parent cycle; `visited` bounds the walk so a cycle
        // degrades to a partial sum instead of recursing forever.
        Set<String> visited = new HashSet<>();
        visited.add(item.getExternalId());

        Accumulator acc = new Accumulator();
        accumulate(directChildren, childrenByParent, visited, acc);

        return new WorkItemHierarchyMetrics(
                true,
                acc.complexityCount > 0 ? acc.complexity : null,
                acc.effortCount > 0 ? acc.effort : null,
                acc.descendants);
    }

    private void accumulate(List<WorkItem> children, Map<String, List<WorkItem>> childrenByParent,
                            Set<String> visited, Accumulator acc) {
        for (WorkItem child : children) {
            if (child.getExternalId() != null && !visited.add(child.getExternalId())) continue;
            acc.descendants += 1;
            if (child.getComplexity() != null) {
                acc.complexity += child.getComplexity();
                acc.complexityCount += 1;
            }
            if (child.getEffort() != null) {
                acc.effort += child.getEffort();
                acc.effortCount += 1;
            }
            List<WorkItem> grandChildren = childrenByParent.getOrDefault(child.getExternalId(), List.of());
            if (!grandChildren.isEmpty()) {
                accumulate(grandChildren, childrenByParent, visited, acc);
            }
        }
    }

    private static final class Accumulator {
        double complexity;
        int complexityCount;
        double effort;
        int effortCount;
        int descendants;
    }
}
