package andromeda.sei.ms.feature.workitem.application.usecase;

import andromeda.sei.ms.feature.workitem.domain.Sprint;
import andromeda.sei.ms.feature.workitem.domain.SprintMetrics;
import andromeda.sei.ms.feature.workitem.domain.SprintMetricsRepository;
import andromeda.sei.ms.feature.workitem.domain.SprintRepository;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.domain.WorkItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CalculateSprintMetricsUseCase {

    private final WorkItemRepository workItemRepository;
    private final SprintRepository sprintRepository;
    private final SprintMetricsRepository sprintMetricsRepository;

    public void execute(String organizationId, String projectId) {
        List<Sprint> sprints = sprintRepository.findAllByOrganizationIdAndProjectId(organizationId, projectId);
        List<WorkItem> allItems = workItemRepository.findAllByOrganizationIdAndProjectId(organizationId, projectId);

        Map<String, List<WorkItem>> itemsBySprintId = allItems.stream()
                .filter(item -> item.getSprintId() != null)
                .collect(Collectors.groupingBy(WorkItem::getSprintId));

        for (Sprint sprint : sprints) {
            List<WorkItem> sprintItems = itemsBySprintId.getOrDefault(sprint.getId(), List.of());
            SprintMetrics metrics = computeMetrics(organizationId, projectId, sprint, sprintItems);
            sprintMetricsRepository.save(metrics);
        }
    }

    private SprintMetrics computeMetrics(String organizationId, String projectId,
                                         Sprint sprint, List<WorkItem> items) {
        List<WorkItem> doneItems = items.stream()
                .filter(i -> i.getResolvedStatus() == WorkItem.ResolvedStatus.DONE)
                .toList();

        double velocity = doneItems.stream()
                .filter(i -> i.getStoryPoints() != null)
                .mapToDouble(WorkItem::getStoryPoints)
                .sum();

        List<Double> cycleTimes = doneItems.stream()
                .filter(i -> i.getCycleTimeHours() != null)
                .map(WorkItem::getCycleTimeHours)
                .toList();

        List<Double> leadTimes = doneItems.stream()
                .filter(i -> i.getLeadTimeHours() != null)
                .map(WorkItem::getLeadTimeHours)
                .toList();

        Double avgCycleTime = cycleTimes.isEmpty() ? null
                : cycleTimes.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        Double avgLeadTime = leadTimes.isEmpty() ? null
                : leadTimes.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);

        int completedItemCount = doneItems.size();
        int totalItemCount = items.size();
        int itemsWithPoints = (int) items.stream().filter(i -> i.getStoryPoints() != null).count();

        return new SprintMetrics(
                null, organizationId, projectId, sprint.getId(),
                WorkItem.Source.AZURE_DEVOPS, velocity, avgCycleTime, avgLeadTime,
                completedItemCount, totalItemCount, itemsWithPoints,
                Instant.now(), true, null, null, null
        );
    }
}
