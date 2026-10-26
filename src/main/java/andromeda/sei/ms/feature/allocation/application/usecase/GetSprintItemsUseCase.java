package andromeda.sei.ms.feature.allocation.application.usecase;

import andromeda.sei.ms.feature.allocation.application.dto.SprintItemOutput;
import andromeda.sei.ms.feature.workitem.application.usecase.EffortBearingLevelResolver;
import andromeda.sei.ms.feature.workitem.domain.Sprint;
import andromeda.sei.ms.feature.workitem.domain.SprintRepository;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.domain.WorkItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GetSprintItemsUseCase {

    private final SprintRepository sprintRepository;
    private final WorkItemRepository workItemRepository;
    private final EffortBearingLevelResolver effortBearingLevelResolver;

    public List<SprintItemOutput> execute(String organizationId, String userId) {
        List<Sprint> currentSprints = sprintRepository.findCurrentSprintsByOrganizationId(organizationId);

        Map<String, String> sprintNameById = currentSprints.stream()
                .collect(Collectors.toMap(Sprint::getId, Sprint::getName));

        List<SprintItemOutput> results = new ArrayList<>();
        for (Sprint sprint : currentSprints) {
            List<WorkItem> items = workItemRepository
                    .findByOrganizationIdAndSprintIdAndAssigneeUserIdAndResolvedStatus(
                            organizationId, sprint.getId(), userId, WorkItem.ResolvedStatus.IN_PROGRESS).stream()
                    .filter(WorkItem::isActive)
                    .toList();
            if (items.isEmpty()) continue;
            // Child Tasks may belong to someone else: roll up against the whole sprint, not the user's slice.
            Map<String, WorkItem> context = new LinkedHashMap<>();
            if (sprint.getProjectId() != null) {
                workItemRepository.findAllByOrganizationIdAndProjectIdAndSprintId(
                                organizationId, sprint.getProjectId(), sprint.getId()).stream()
                        .filter(WorkItem::isActive)
                        .forEach(wi -> context.putIfAbsent(wi.getId(), wi));
            }
            items.forEach(wi -> context.putIfAbsent(wi.getId(), wi));
            var scope = effortBearingLevelResolver.scope(new ArrayList<>(context.values()));
            for (WorkItem item : items) {
                String sprintName = sprintNameById.getOrDefault(item.getSprintId(), sprint.getName());
                results.add(new SprintItemOutput(
                        item.getId(),
                        item.getTitle(),
                        sprintName,
                        item.getResolvedType() != null ? item.getResolvedType().name() : null,
                        item.getExternalId(),
                        item.getEffort() != null ? item.getEffort() : item.getStoryPoints(),
                        scope.effectiveComplexity(item),
                        scope.taskEffort(item)));
            }
        }
        return results;
    }
}
