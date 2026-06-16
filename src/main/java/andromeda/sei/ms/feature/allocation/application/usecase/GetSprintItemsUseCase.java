package andromeda.sei.ms.feature.allocation.application.usecase;

import andromeda.sei.ms.feature.allocation.application.dto.SprintItemOutput;
import andromeda.sei.ms.feature.workitem.domain.Sprint;
import andromeda.sei.ms.feature.workitem.domain.SprintRepository;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.domain.WorkItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GetSprintItemsUseCase {

    private final SprintRepository sprintRepository;
    private final WorkItemRepository workItemRepository;

    public List<SprintItemOutput> execute(String organizationId, String userId) {
        List<Sprint> currentSprints = sprintRepository.findCurrentSprintsByOrganizationId(organizationId);

        Map<String, String> sprintNameById = currentSprints.stream()
                .collect(Collectors.toMap(Sprint::getId, Sprint::getName));

        List<SprintItemOutput> results = new ArrayList<>();
        for (Sprint sprint : currentSprints) {
            List<WorkItem> items = workItemRepository
                    .findByOrganizationIdAndSprintIdAndAssigneeUserIdAndResolvedStatus(
                            organizationId, sprint.getId(), userId, WorkItem.ResolvedStatus.IN_PROGRESS);
            for (WorkItem item : items) {
                String sprintName = sprintNameById.getOrDefault(item.getSprintId(), sprint.getName());
                results.add(new SprintItemOutput(item.getId(), item.getTitle(), sprintName));
            }
        }
        return results;
    }
}
