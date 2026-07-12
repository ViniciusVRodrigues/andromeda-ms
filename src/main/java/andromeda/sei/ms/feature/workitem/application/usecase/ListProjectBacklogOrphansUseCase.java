package andromeda.sei.ms.feature.workitem.application.usecase;

import andromeda.sei.ms.feature.azure.domain.AzureProjectBindingRepository;
import andromeda.sei.ms.feature.azure.domain.HierarchyLevel;
import andromeda.sei.ms.feature.project.domain.ProjectRepository;
import andromeda.sei.ms.feature.project.domain.exception.ProjectNotFoundException;
import andromeda.sei.ms.feature.workitem.application.classifier.WorkItemHierarchyClassifier;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.domain.WorkItemPageResult;
import andromeda.sei.ms.feature.workitem.domain.WorkItemRepository;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Function;

/**
 * Lists the paginated "orphan" work items for a project's backlog tree — items whose
 * parentExternalId either doesn't resolve within the project or violates the declared hierarchy
 * order, per openspec/changes/project-backlog-view/design.md Decision 3.
 */
@Component
@RequiredArgsConstructor
public class ListProjectBacklogOrphansUseCase {

    private final ProjectRepository projectRepository;
    private final AzureProjectBindingRepository azureProjectBindingRepository;
    private final WorkItemRepository workItemRepository;
    private final WorkItemHierarchyClassifier classifier;

    public WorkItemPageResult execute(String projectId, int page, int size) {
        String orgId = TenantContextHolder.get();

        var project = projectRepository.findById(projectId)
                .filter(p -> p.getOrganizationId().equals(orgId) && p.isActive())
                .orElseThrow(() -> new ProjectNotFoundException(projectId));

        List<HierarchyLevel> hierarchyLevels = resolveHierarchyLevels(orgId, project.getId());

        WorkItemPageResult candidatePage = workItemRepository
                .findRootCandidatesByOrganizationIdAndProjectId(orgId, project.getId(), page, size);

        Function<String, WorkItem> resolver = externalId -> workItemRepository
                .findByOrganizationIdAndExternalIdAndSource(orgId, externalId, WorkItem.Source.AZURE_DEVOPS)
                .orElse(null);

        List<WorkItem> orphans = candidatePage.getContent().stream()
                .filter(item -> classifier.classify(item, hierarchyLevels, resolver) == WorkItemHierarchyClassifier.Classification.ORPHAN)
                .toList();

        return new WorkItemPageResult(orphans, candidatePage.getPage(), candidatePage.getSize(), candidatePage.getTotalElements());
    }

    private List<HierarchyLevel> resolveHierarchyLevels(String orgId, String projectId) {
        return azureProjectBindingRepository
                .findByOrganizationIdAndProjectIdAndActiveTrue(orgId, projectId)
                .map(b -> b.getWorkflowConfig() != null ? b.getWorkflowConfig().getHierarchyLevels() : List.<HierarchyLevel>of())
                .orElse(List.of());
    }
}
