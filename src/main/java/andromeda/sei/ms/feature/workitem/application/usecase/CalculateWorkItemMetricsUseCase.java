package andromeda.sei.ms.feature.workitem.application.usecase;

import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.domain.WorkItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CalculateWorkItemMetricsUseCase {

    private static final double MILLIS_PER_HOUR = 3_600_000.0;
    private static final double ROUNDING_SCALE = 10_000.0;

    private final WorkItemRepository workItemRepository;

    public void execute(String organizationId, String projectId) {
        List<WorkItem> items = workItemRepository.findAllByOrganizationIdAndProjectId(organizationId, projectId);

        for (WorkItem item : items) {
            Double cycleTimeHours = computeCycleTime(item);
            Double leadTimeHours = computeLeadTime(item);

            if (cycleTimeHours != null && cycleTimeHours < 0) {
                log.warn("Negative cycleTimeHours for workItem id={} org={}: startedAt > completedAt",
                        item.getId(), organizationId);
            }

            workItemRepository.updateMetrics(item.getId(), cycleTimeHours, leadTimeHours);
        }
    }

    private Double computeCycleTime(WorkItem item) {
        if (item.getCompletedAt() == null || item.getStartedAt() == null) {
            return null;
        }
        double raw = (item.getCompletedAt().toEpochMilli() - item.getStartedAt().toEpochMilli()) / MILLIS_PER_HOUR;
        return Math.round(raw * ROUNDING_SCALE) / ROUNDING_SCALE;
    }

    private Double computeLeadTime(WorkItem item) {
        if (item.getCompletedAt() == null) {
            return null;
        }
        double raw = (item.getCompletedAt().toEpochMilli() - item.getCreatedAt().toEpochMilli()) / MILLIS_PER_HOUR;
        return Math.round(raw * ROUNDING_SCALE) / ROUNDING_SCALE;
    }
}
