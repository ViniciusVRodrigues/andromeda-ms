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
                        .filter(wi -> wi.getResolvedType() == WorkItem.ResolvedType.STORY)
                        .map(wi -> new WorkItemCandidateOutput(wi.getId(), wi.getTitle(), sprint.getName())))
                .toList();
    }
}
