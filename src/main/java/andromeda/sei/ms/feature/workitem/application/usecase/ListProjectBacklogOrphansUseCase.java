package andromeda.sei.ms.feature.workitem.application.usecase;

import andromeda.sei.ms.feature.workitem.application.classifier.WorkItemHierarchyClassifier.Classification;
import andromeda.sei.ms.feature.workitem.domain.WorkItemPageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

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
        return classificationService.execute(projectId, Classification.ORPHAN, page, size);
    }
}
