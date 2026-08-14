package andromeda.sei.ms.feature.workitem.application.usecase;

import andromeda.sei.ms.feature.workitem.application.classifier.WorkItemHierarchyClassifier.Classification;
import andromeda.sei.ms.feature.workitem.domain.WorkItemPageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Lists the paginated "root" work items for a project's backlog tree, per
 * openspec/changes/project-backlog-view/design.md Decision 1 &amp; 3.
 */
@Component
@RequiredArgsConstructor
public class ListProjectBacklogRootsUseCase {

    private final ProjectBacklogClassificationService classificationService;

    public WorkItemPageResult execute(String projectId, int page, int size) {
        return execute(projectId, page, size, BacklogFilter.NONE);
    }

    /**
     * Com filtro ativo o resultado é a lista plana de todos os itens que casam (ver
     * {@link ProjectBacklogClassificationService}), e não apenas as raízes.
     */
    public WorkItemPageResult execute(String projectId, int page, int size, BacklogFilter filter) {
        return classificationService.execute(projectId, Classification.ROOT, page, size, filter);
    }
}
