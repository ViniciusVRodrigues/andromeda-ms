package andromeda.sei.ms.feature.workitem.application.usecase;

import andromeda.sei.ms.feature.azure.domain.AzureProjectBindingRepository;
import andromeda.sei.ms.feature.azure.domain.HierarchyLevel;
import andromeda.sei.ms.feature.project.domain.ProjectRepository;
import andromeda.sei.ms.feature.project.domain.exception.ProjectNotFoundException;
import andromeda.sei.ms.feature.workitem.application.classifier.BacklogOrdering;
import andromeda.sei.ms.feature.workitem.application.classifier.WorkItemHierarchyClassifier;
import andromeda.sei.ms.feature.workitem.application.classifier.WorkItemHierarchyClassifier.HierarchyPlacement;
import andromeda.sei.ms.feature.workitem.domain.BacklogItemInfo;
import andromeda.sei.ms.feature.workitem.domain.BacklogLevel;
import andromeda.sei.ms.feature.workitem.application.classifier.WorkItemHierarchyClassifier.Classification;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.domain.WorkItemHierarchyMetrics;
import andromeda.sei.ms.feature.workitem.domain.WorkItemPageResult;
import andromeda.sei.ms.feature.workitem.domain.WorkItemRepository;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
        return execute(projectId, classification, page, size, BacklogFilter.NONE);
    }

    /**
     * Com {@code filter} vazio, serve a visão em árvore: apenas os itens da classificação pedida
     * (ROOT ou ORPHAN), que a UI expande sob demanda.
     *
     * <p>Com qualquer critério informado, a classificação é <strong>ignorada</strong> e o
     * resultado é uma lista plana de todos os itens que casam, independentemente de serem raiz,
     * filho ou órfão. Isso é intencional: ao filtrar por sprint ou buscar por id, a intenção
     * deixa de ser navegar a hierarquia e passa a ser localizar itens — e uma árvore podada
     * levantaria a pergunta insolúvel de o que fazer com um pai que não casa mas tem filhos que
     * casam. Achatar elimina a ambiguidade e torna o total honesto por construção.
     *
     * <p>O filtro é aplicado <em>antes</em> da paginação, então {@code totalElements} conta o
     * conjunto filtrado inteiro — e não a página, nem o backlog todo.
     */
    public WorkItemPageResult execute(String projectId, Classification classification, int page, int size,
                                       BacklogFilter filter) {
        String orgId = TenantContextHolder.get();

        var project = projectRepository.findById(projectId)
                .filter(p -> p.getOrganizationId().equals(orgId) && p.isActive())
                .orElseThrow(() -> new ProjectNotFoundException(projectId));

        List<HierarchyLevel> hierarchyLevels = resolveHierarchyLevels(orgId, project.getId());

        List<WorkItem> allItems = workItemRepository.findAllByOrganizationIdAndProjectId(orgId, project.getId())
                .stream()
                .filter(WorkItem::isActive)
                .toList();

        Map<String, WorkItem> byExternalId = new HashMap<>();
        for (WorkItem item : allItems) {
            if (item.getExternalId() != null) byExternalId.putIfAbsent(item.getExternalId(), item);
        }
        Map<String, HierarchyPlacement> placements = classifier.classifyAll(allItems, hierarchyLevels);
        Function<String, Integer> rankLookup = classifier.rankLookup(hierarchyLevels);
        Function<WorkItem, Integer> rankOf = item -> rankLookup.apply(item.getRawType());

        List<WorkItem> matching = (filter.isEmpty()
                ? allItems.stream()
                        .filter(item -> placements.get(item.getId()).classification() == classification)
                : allItems.stream()
                        .filter(filter::matches))
                .sorted(BacklogOrdering.comparator(rankOf))
                .toList();

        long totalElements = matching.size();
        int fromIndex = (int) Math.min((long) page * size, matching.size());
        int toIndex = Math.min(fromIndex + size, matching.size());
        List<WorkItem> pageContent = matching.subList(fromIndex, toIndex);

        // Computed over the full project set (already in memory here) so rollups see every
        // descendant, not just the ones on this page; then narrowed to the page being returned.
        Map<String, WorkItemHierarchyMetrics> allMetrics =
                metricsCalculator.calculate(allItems, placements);
        Map<String, WorkItemHierarchyMetrics> pageMetrics = new HashMap<>();
        Map<String, BacklogItemInfo> pageInfo = new HashMap<>();
        for (WorkItem item : pageContent) {
            WorkItemHierarchyMetrics metrics = allMetrics.get(item.getId());
            if (metrics != null) pageMetrics.put(item.getId(), metrics);
            pageInfo.put(item.getId(), infoOf(item, placements, byExternalId, rankOf));
        }

        return new WorkItemPageResult(pageContent, page, size, totalElements, pageMetrics, pageInfo,
                levelsOf(matching, rankOf));
    }

    /** Rank, parentMissing and parentRawType of one item (backlog-arvore-nivel D3). */
    static BacklogItemInfo infoOf(WorkItem item, Map<String, HierarchyPlacement> placements,
                                  Map<String, WorkItem> byExternalId, Function<WorkItem, Integer> rankOf) {
        HierarchyPlacement placement = placements.get(item.getId());
        WorkItem parent = item.getParentExternalId() == null
                || item.getParentExternalId().equals(item.getExternalId())
                ? null
                : byExternalId.get(item.getParentExternalId());
        return new BacklogItemInfo(rankOf.apply(item),
                placement != null && placement.parentMissing(),
                parent == null ? null : parent.getRawType());
    }

    /** One entry per rank in the whole (sorted) list, in the same order, null rank last. */
    private static List<BacklogLevel> levelsOf(List<WorkItem> sorted, Function<WorkItem, Integer> rankOf) {
        Map<Optional<Integer>, List<WorkItem>> byRank = new LinkedHashMap<>();
        for (WorkItem item : sorted) {
            byRank.computeIfAbsent(Optional.ofNullable(rankOf.apply(item)), k -> new ArrayList<>()).add(item);
        }
        List<BacklogLevel> levels = new ArrayList<>(byRank.size());
        byRank.forEach((rank, items) -> levels.add(new BacklogLevel(
                rank.orElse(null),
                items.stream().map(WorkItem::getRawType).filter(Objects::nonNull).distinct()
                        .sorted(String.CASE_INSENSITIVE_ORDER.thenComparing(Comparator.naturalOrder()))
                        .toList(),
                items.size())));
        return levels;
    }

    private List<HierarchyLevel> resolveHierarchyLevels(String orgId, String projectId) {
        return azureProjectBindingRepository
                .findByOrganizationIdAndProjectIdAndActiveTrue(orgId, projectId)
                .map(b -> b.getWorkflowConfig() != null ? b.getWorkflowConfig().getHierarchyLevels() : List.<HierarchyLevel>of())
                .orElse(List.of());
    }
}
