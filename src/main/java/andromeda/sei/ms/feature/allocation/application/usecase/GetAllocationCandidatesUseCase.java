package andromeda.sei.ms.feature.allocation.application.usecase;

import andromeda.sei.ms.feature.allocation.application.dto.WorkItemCandidateOutput;
import andromeda.sei.ms.feature.workitem.domain.Sprint;
import andromeda.sei.ms.feature.workitem.domain.SprintRepository;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.domain.WorkItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GetAllocationCandidatesUseCase {

    private final SprintRepository sprintRepository;
    private final WorkItemRepository workItemRepository;

    public List<WorkItemCandidateOutput> execute(String organizationId, String userId, LocalDate day) {
        List<Sprint> currentSprints = sprintRepository.findCurrentSprintsByOrganizationId(organizationId);

        return currentSprints.stream()
                .flatMap(sprint -> workItemRepository
                        .findByOrganizationIdAndSprintIdAndAssigneeUserIdAndResolvedStatus(
                                organizationId, sprint.getId(), userId, WorkItem.ResolvedStatus.IN_PROGRESS)
                        .stream()
                        .map(wi -> new WorkItemCandidateOutput(wi.getId(), wi.getTitle(), sprint.getName())))
                .toList();
    }
}
