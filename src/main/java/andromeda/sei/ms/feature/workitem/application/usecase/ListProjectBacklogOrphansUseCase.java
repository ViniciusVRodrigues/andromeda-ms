package andromeda.sei.ms.feature.workitem.application.usecase;

import andromeda.sei.ms.feature.workitem.application.classifier.WorkItemHierarchyClassifier.Classification;
import andromeda.sei.ms.feature.workitem.domain.WorkItemPageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Lists the paginated "orphan" work items for a project's backlog tree — items whose
 * parentExternalId either doesn't resolve within the project or violates the declared hierarchy
 * order, per openspec/changes/project-backlog-view/design.md Decision 3.
 */
@Component
@RequiredArgsConstructor
public class ListProjectBacklogOrphansUseCase {

    private final ProjectBacklogClassificationService classificationService;

    public WorkItemPageResult execute(String projectId, int page, int size) {
        return execute(projectId, page, size, BacklogFilter.NONE);
    }

    /**
     * Devolve vazio quando há filtro ativo. Sob filtro a listagem vira uma lista plana única,
     * servida pelo endpoint de raízes — e órfãos já estão incluídos nela. Repetir a mesma lista
     * aqui duplicaria cada item na tela.
     */
    public WorkItemPageResult execute(String projectId, int page, int size, BacklogFilter filter) {
        if (!filter.isEmpty()) {
            return new WorkItemPageResult(List.of(), page, size, 0, Map.of());
        }
        return classificationService.execute(projectId, Classification.ORPHAN, page, size);
    }
}
