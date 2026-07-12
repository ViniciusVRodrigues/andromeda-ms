package andromeda.sei.ms.feature.workitem.application.usecase;

import andromeda.sei.ms.feature.azure.domain.AzureProjectBindingRepository;
import andromeda.sei.ms.feature.azure.domain.HierarchyLevel;
import andromeda.sei.ms.feature.workitem.application.classifier.WorkItemHierarchyClassifier;
import andromeda.sei.ms.feature.workitem.application.dto.WorkItemChildrenOutput;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.domain.WorkItemRepository;
import andromeda.sei.ms.feature.workitem.domain.exception.WorkItemNotFoundException;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
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

    public WorkItemChildrenOutput execute(String workItemId) {
        String orgId = TenantContextHolder.get();

        WorkItem parent = workItemRepository.findById(workItemId)
                .filter(w -> w.getOrganizationId().equals(orgId) && w.isActive())
                .orElseThrow(() -> new WorkItemNotFoundException(workItemId));

        List<HierarchyLevel> hierarchyLevels = azureProjectBindingRepository
                .findByOrganizationIdAndProjectIdAndActiveTrue(orgId, parent.getProjectId())
                .map(b -> b.getWorkflowConfig() != null ? b.getWorkflowConfig().getHierarchyLevels() : List.<HierarchyLevel>of())
                .orElse(List.of());

        // Fetch one extra beyond the cap to detect truncation without a separate count query.
        List<WorkItem> candidates = workItemRepository.findChildCandidatesByOrganizationIdAndParentExternalId(
                orgId, parent.getProjectId(), parent.getExternalId(), CHILDREN_SOFT_CAP + 1);

        Function<String, WorkItem> resolver = externalId -> externalId.equals(parent.getExternalId())
                ? parent
                : workItemRepository.findByOrganizationIdAndExternalIdAndSource(orgId, externalId, WorkItem.Source.AZURE_DEVOPS)
                        .orElse(null);

        List<WorkItem> validChildren = candidates.stream()
                .filter(item -> classifier.classify(item, hierarchyLevels, resolver) == WorkItemHierarchyClassifier.Classification.CHILD)
                .toList();

        boolean truncated = validChildren.size() > CHILDREN_SOFT_CAP;
        List<WorkItem> page = truncated ? validChildren.subList(0, CHILDREN_SOFT_CAP) : validChildren;

        return new WorkItemChildrenOutput(page, truncated);
    }
}
