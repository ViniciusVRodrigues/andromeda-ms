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
    private final andromeda.sei.ms.feature.workitem.application.usecase.EffortBearingLevelResolver
            effortBearingLevelResolver;

    public List<WorkItemCandidateOutput> execute(String organizationId, String userId, LocalDate day) {
        List<Sprint> currentSprints = new ArrayList<>(
                sprintRepository.findByOrganizationIdAndDay(organizationId, day));
        if (currentSprints.isEmpty()) {
            sprintRepository.findClosestSprintByOrganizationIdAndDay(organizationId, day)
                    .ifPresent(currentSprints::add);
        }

        return currentSprints.stream()
                .flatMap(sprint -> {
                    List<WorkItem> sprintItems = workItemRepository
                            .findByOrganizationIdAndAssigneeUserIdAndSprintId(
                                    organizationId, userId, sprint.getId()).stream()
                            .filter(WorkItem::isActive)
                            .toList();
                    // Candidates must match the level the team actually books work at: when effort
                    // lives on Tasks, offering only Stories left the developer with nothing to
                    // attribute their AI usage to.
                    var level = effortBearingLevelResolver.resolve(null, sprintItems);
                    var scope = effortBearingLevelResolver.scope(sprintItems);
                    return effortBearingLevelResolver.deliverableUnits(level, sprintItems).stream()
                            .map(wi -> new WorkItemCandidateOutput(
                                    wi.getId(),
                                    wi.getTitle(),
                                    sprint.getName(),
                                    wi.getResolvedType() != null ? wi.getResolvedType().name() : null,
                                    wi.getExternalId(),
                                    scope.effectiveComplexity(wi),
                                    scope.taskEffort(wi)));
                })
                .toList();
    }
}
