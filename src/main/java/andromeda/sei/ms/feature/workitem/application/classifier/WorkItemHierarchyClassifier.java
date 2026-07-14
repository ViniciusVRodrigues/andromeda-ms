package andromeda.sei.ms.feature.workitem.application.classifier;

import andromeda.sei.ms.feature.azure.domain.HierarchyLevel;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Applies the two-tier root/child/orphan classification rule described in
 * openspec/changes/project-backlog-view/design.md Decision 3.
 *
 * <p>Rank-aware tier (used when the binding declares {@code hierarchyLevels}):
 * <ul>
 *   <li>Root: the item's type has the lowest declared rank present among the types found in the
 *       project's synced data, OR the item has no {@code parentExternalId}.</li>
 *   <li>Valid child: {@code parentExternalId} resolves to another work item in the same project
 *       AND that parent's declared rank is strictly lower (higher in the tree) than the item's own
 *       rank.</li>
 *   <li>Orphan: {@code parentExternalId} is non-null and either doesn't resolve within the project,
 *       or resolves but violates the "strictly lower" rank ordering (a hierarchy-violating link).</li>
 *   <li>Per-item fallback: if an item's raw type has no entry in {@code hierarchyLevels} (e.g. a new
 *       custom type added after onboarding), that single item is classified using the structural
 *       fallback rule below, without affecting classification of other, ranked items.</li>
 * </ul>
 *
 * <p>Structural fallback tier (used when {@code hierarchyLevels} is empty/absent, or per-item per
 * above): root = no resolvable {@code parentExternalId}; any resolvable {@code parentExternalId} is
 * a valid child regardless of type; orphan = non-null {@code parentExternalId} that doesn't resolve.
 */
@Component
public class WorkItemHierarchyClassifier {

    public enum Classification { ROOT, CHILD, ORPHAN }

    /**
     * Classifies a single work item given the set of hierarchy levels declared on its binding and
     * a resolver capable of finding another work item in the same project by its externalId (used
     * to look up the candidate parent, when present).
     */
    public Classification classify(WorkItem item, List<HierarchyLevel> hierarchyLevels,
                                    Function<String, WorkItem> resolveByExternalId) {
        Map<String, Integer> ranksByRawType = ranksByRawType(hierarchyLevels);
        boolean hierarchyConfigured = !ranksByRawType.isEmpty();

        if (!hierarchyConfigured) {
            return classifyStructural(item, resolveByExternalId);
        }

        Integer itemRank = ranksByRawType.get(normalize(item.getRawType()));
        if (itemRank == null) {
            // 4.5: per-item fallback — this item's type has no declared rank, classify it
            // structurally without disturbing rank-aware classification of other items.
            return classifyStructural(item, resolveByExternalId);
        }

        int lowestRank = ranksByRawType.values().stream().mapToInt(Integer::intValue).min().orElseThrow();

        if (item.getParentExternalId() == null) {
            return Classification.ROOT;
        }

        WorkItem parent = resolveByExternalId.apply(item.getParentExternalId());
        if (parent == null) {
            return Classification.ORPHAN;
        }

        Integer parentRank = ranksByRawType.get(normalize(parent.getRawType()));
        if (parentRank != null && parentRank < itemRank) {
            return Classification.CHILD;
        }
        if (parentRank == null && itemRank == lowestRank) {
            // Parent's type is itself unranked; treat this item as root only if it's already at
            // the lowest declared rank (no valid ranked parent could exist above it anyway).
            return Classification.ROOT;
        }
        // parent resolves but violates "strictly lower" rank ordering — hierarchy-violating link.
        return Classification.ORPHAN;
    }

    private Classification classifyStructural(WorkItem item, Function<String, WorkItem> resolveByExternalId) {
        if (item.getParentExternalId() == null) {
            return Classification.ROOT;
        }
        WorkItem parent = resolveByExternalId.apply(item.getParentExternalId());
        return parent != null ? Classification.CHILD : Classification.ORPHAN;
    }

    private Map<String, Integer> ranksByRawType(List<HierarchyLevel> hierarchyLevels) {
        if (hierarchyLevels == null || hierarchyLevels.isEmpty()) {
            return Map.of();
        }
        return hierarchyLevels.stream()
                .collect(java.util.stream.Collectors.toMap(
                        level -> normalize(level.getRawType()), HierarchyLevel::getRank, (a, b) -> a));
    }

    // Azure's raw type names are matched case/whitespace-insensitively everywhere else in this
    // codebase (see SyncAzureWorkItemsUseCase.resolveType/resolveStatus); hierarchyLevels lookups
    // must do the same, since the wizard's discovered-type strings and a WorkItem's persisted
    // rawType can otherwise drift in casing and silently miss the rank map.
    private String normalize(String rawType) {
        return rawType == null ? null : rawType.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
