package andromeda.sei.ms.feature.allocation.application.usecase;

import andromeda.sei.ms.feature.allocation.application.dto.WorkItemCandidateOutput;
import andromeda.sei.ms.feature.workitem.domain.Sprint;
import andromeda.sei.ms.feature.workitem.domain.SprintRepository;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.domain.WorkItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GetAllocationCandidatesUseCase {

    private final SprintRepository sprintRepository;
    private final WorkItemRepository workItemRepository;

    public List<WorkItemCandidateOutput> execute(String organizationId, String userId, LocalDate day) {
        List<Sprint> currentSprints = new ArrayList<>(
                sprintRepository.findByOrganizationIdAndDay(organizationId, day));
        if (currentSprints.isEmpty()) {
            sprintRepository.findClosestSprintByOrganizationIdAndDay(organizationId, day)
                    .ifPresent(currentSprints::add);
        }

        return currentSprints.stream()
                .flatMap(sprint -> workItemRepository
                        .findByOrganizationIdAndAssigneeUserIdAndSprintId(
                                organizationId, userId, sprint.getId())
                        .stream()
                        .filter(wi -> isStoryTierWork(wi.getResolvedType()))
                        .map(wi -> new WorkItemCandidateOutput(wi.getId(), wi.getTitle(), sprint.getName())))
                .toList();
    }

    /**
     * STORY and FEATURE are both developer-assignable, effort-bearing deliverable units — a
     * Feature-typed work item is just as legitimate an AI-allocation candidate as a Story in
     * Feature-heavy custom process templates (see GetTeamDashboardUseCase.isStoryTierWork for
     * the same reasoning applied to dashboards).
     */
    private boolean isStoryTierWork(WorkItem.ResolvedType resolvedType) {
        return resolvedType == WorkItem.ResolvedType.STORY || resolvedType == WorkItem.ResolvedType.FEATURE;
    }
}
