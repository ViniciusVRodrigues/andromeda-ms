package andromeda.sei.ms.feature.allocation.application;

import andromeda.sei.ms.feature.azure.domain.AzureProjectBinding;
import andromeda.sei.ms.feature.azure.domain.AzureProjectBindingRepository;
import andromeda.sei.ms.feature.azure.domain.TypeMapping;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Single place that decides which work items may receive AI allocation. When the project's Azure
 * binding marks at least one type with {@code receivesAi}, only active items of those raw types
 * (case-insensitive, regardless of level) are eligible; with none marked, or without a binding,
 * the caller's default rule applies unchanged. Bindings are loaded once per call, never cached
 * globally: the wizard changes them and nothing invalidates a cache.
 */
@Component
@RequiredArgsConstructor
public class AllocationTypeEligibility {

    private final AzureProjectBindingRepository bindingRepository;

    public List<WorkItem> eligibleUnits(String organizationId, Collection<WorkItem> items,
                                        Function<List<WorkItem>, List<WorkItem>> defaultRule) {
        if (items == null || items.isEmpty()) return List.of();
        Map<String, List<WorkItem>> byProject = new LinkedHashMap<>();
        for (WorkItem item : items) {
            byProject.computeIfAbsent(String.valueOf(item.getProjectId()), k -> new java.util.ArrayList<>()).add(item);
        }
        Map<String, Set<String>> marked = new HashMap<>();
        Set<WorkItem> kept = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (List<WorkItem> group : byProject.values()) {
            Set<String> types = receivesAiTypes(organizationId, group.get(0).getProjectId(), marked);
            if (types.isEmpty()) {
                kept.addAll(defaultRule.apply(group));
            } else {
                group.stream().filter(i -> i.isActive() && matches(types, i)).forEach(kept::add);
            }
        }
        return items.stream().filter(kept::contains).toList();
    }

    public boolean isEligible(String organizationId, WorkItem item) {
        Set<String> types = receivesAiTypes(organizationId, item.getProjectId(), new HashMap<>());
        return types.isEmpty() || matches(types, item);
    }

    private Set<String> receivesAiTypes(String organizationId, String projectId, Map<String, Set<String>> local) {
        return local.computeIfAbsent(String.valueOf(projectId), k -> {
            Set<String> types = new HashSet<>();
            if (projectId == null) return types;
            bindingRepository.findByOrganizationIdAndProjectIdAndActiveTrue(organizationId, projectId)
                    .map(AzureProjectBinding::getWorkflowConfig)
                    .map(c -> c.getTypeMappings())
                    .ifPresent(mappings -> mappings.stream()
                            .filter(TypeMapping::isReceivesAi)
                            .map(TypeMapping::getRawType)
                            .filter(java.util.Objects::nonNull)
                            .map(t -> t.toLowerCase(java.util.Locale.ROOT))
                            .forEach(types::add));
            return types;
        });
    }

    private static boolean matches(Set<String> types, WorkItem item) {
        return item.getRawType() != null && types.contains(item.getRawType().toLowerCase(java.util.Locale.ROOT));
    }
}
