package andromeda.sei.ms.feature.workitem.application.classifier;

import andromeda.sei.ms.feature.azure.domain.HierarchyLevel;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Root/child/orphan classification of a project's work items
 * (openspec/changes/backlog-arvore-nivel/design.md D1). {@code rank(x)} is the rank of the raw type
 * in the binding's {@code hierarchyLevels}, case-insensitive, or null. In order:
 * <ol>
 *   <li>no parent, or parent equal to the item itself: ROOT;</li>
 *   <li>parent not among the project's active items: ROOT with {@code parentMissing};</li>
 *   <li>no {@code hierarchyLevels}, or {@code rank(item)} / {@code rank(parent)} null: CHILD;</li>
 *   <li>{@code rank(parent) <= rank(item)}: CHILD (same level is allowed);</li>
 *   <li>{@code rank(parent) > rank(item)}: ORPHAN (inverted hierarchy);</li>
 *   <li>cycle through CHILD links (at most {@link #MAX_HOPS} hops): the lowest id of the cycle is
 *       ROOT, the others stay CHILD.</li>
 * </ol>
 */
@Component
public class WorkItemHierarchyClassifier {

    /** Upper bound for walking up the parent links when looking for a cycle. */
    static final int MAX_HOPS = 64;

    public enum Classification { ROOT, CHILD, ORPHAN }

    public record HierarchyPlacement(Classification classification, boolean parentMissing) {
    }

    /** Classifies one item against a resolver of the project active items by externalId. */
    public Classification classify(WorkItem item, List<HierarchyLevel> hierarchyLevels,
                                    Function<String, WorkItem> resolveByExternalId) {
        return place(item, hierarchyLevels, resolveByExternalId).classification();
    }

    public HierarchyPlacement place(WorkItem item, List<HierarchyLevel> hierarchyLevels,
                                     Function<String, WorkItem> resolveByExternalId) {
        return place(item, ranksByRawType(hierarchyLevels), resolveByExternalId);
    }

    /**
     * Classifies a whole project at once, keyed by the item {@code id}. Same rules as
     * {@link #place}, with the ranks computed once; callers that need roots, children and rollups
     * to agree must share this result.
     */
    public Map<String, HierarchyPlacement> classifyAll(List<WorkItem> items, List<HierarchyLevel> hierarchyLevels) {
        Map<String, Integer> ranks = ranksByRawType(hierarchyLevels);
        Map<String, WorkItem> byExternalId = new HashMap<>();
        for (WorkItem item : items) {
            if (item.getExternalId() != null) byExternalId.putIfAbsent(item.getExternalId(), item);
        }
        Function<String, WorkItem> resolver = byExternalId::get;
        Map<String, HierarchyPlacement> result = new HashMap<>(items.size() * 2);
        for (WorkItem item : items) {
            result.put(item.getId(), place(item, ranks, resolver));
        }
        return result;
    }

    /** Rank of the item raw type in the hierarchy levels, or {@code null}. */
    public Integer rankOf(WorkItem item, List<HierarchyLevel> hierarchyLevels) {
        return ranksByRawType(hierarchyLevels).get(normalize(item.getRawType()));
    }

    /** Case-insensitive rank lookup by raw type, for callers that rank many items. */
    public Function<String, Integer> rankLookup(List<HierarchyLevel> hierarchyLevels) {
        Map<String, Integer> ranks = ranksByRawType(hierarchyLevels);
        return rawType -> ranks.get(normalize(rawType));
    }

    private HierarchyPlacement place(WorkItem item, Map<String, Integer> ranks,
                                      Function<String, WorkItem> resolver) {
        HierarchyPlacement raw = placeIgnoringCycles(item, ranks, resolver);
        if (raw.classification() == Classification.CHILD && isLowestOfItsCycle(item, ranks, resolver)) {
            return new HierarchyPlacement(Classification.ROOT, false);
        }
        return raw;
    }

    private HierarchyPlacement placeIgnoringCycles(WorkItem item, Map<String, Integer> ranks,
                                                    Function<String, WorkItem> resolver) {
        String parentId = item.getParentExternalId();
        if (parentId == null || parentId.equals(item.getExternalId())) {
            return new HierarchyPlacement(Classification.ROOT, false);
        }
        WorkItem parent = resolver.apply(parentId);
        if (parent == null) {
            return new HierarchyPlacement(Classification.ROOT, true);
        }
        Integer itemRank = ranks.get(normalize(item.getRawType()));
        Integer parentRank = ranks.get(normalize(parent.getRawType()));
        if (ranks.isEmpty() || itemRank == null || parentRank == null || parentRank <= itemRank) {
            return new HierarchyPlacement(Classification.CHILD, false);
        }
        return new HierarchyPlacement(Classification.ORPHAN, false);
    }

    /**
     * Walks up the parent links that would be CHILD. If the path returns to {@code item}, the item
     * is in a cycle, and it is promoted to ROOT when it has the lowest id of the cycle.
     */
    private boolean isLowestOfItsCycle(WorkItem item, Map<String, Integer> ranks,
                                        Function<String, WorkItem> resolver) {
        Set<String> visited = new HashSet<>();
        visited.add(item.getExternalId());
        WorkItem lowest = item;
        WorkItem current = item;
        for (int hop = 0; hop < MAX_HOPS; hop++) {
            if (placeIgnoringCycles(current, ranks, resolver).classification() != Classification.CHILD) {
                return false;
            }
            WorkItem next = resolver.apply(current.getParentExternalId());
            if (next == null) return false;
            if (next.getExternalId().equals(item.getExternalId())) {
                return lowest == item;
            }
            if (!visited.add(next.getExternalId())) {
                return false; // a cycle that does not pass through this item
            }
            if (BacklogOrdering.compareExternalIds(next.getExternalId(), lowest.getExternalId()) < 0) {
                lowest = next;
            }
            current = next;
        }
        return false;
    }

    private Map<String, Integer> ranksByRawType(List<HierarchyLevel> hierarchyLevels) {
        if (hierarchyLevels == null || hierarchyLevels.isEmpty()) {
            return Map.of();
        }
        return hierarchyLevels.stream()
                .collect(java.util.stream.Collectors.toMap(
                        level -> normalize(level.getRawType()), HierarchyLevel::getRank, (a, b) -> a));
    }

    // Azure raw type names are matched case/whitespace-insensitively everywhere else in this
    // codebase (see SyncAzureWorkItemsUseCase.resolveType/resolveStatus); hierarchyLevels lookups
    // must do the same, since the wizard discovered-type strings and a WorkItem persisted
    // rawType can otherwise drift in casing and silently miss the rank map.
    private String normalize(String rawType) {
        return rawType == null ? null : rawType.trim().toLowerCase(Locale.ROOT);
    }
}
