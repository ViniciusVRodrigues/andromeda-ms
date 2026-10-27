package andromeda.sei.ms.feature.workitem.application.usecase;

import andromeda.sei.ms.feature.workitem.domain.SharedIterations;
import andromeda.sei.ms.feature.workitem.domain.Sprint;
import andromeda.sei.ms.feature.workitem.domain.SprintMetrics;
import andromeda.sei.ms.feature.workitem.domain.SprintMetricsRepository;
import andromeda.sei.ms.feature.workitem.domain.SprintRepository;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.domain.WorkItemRepository;
import andromeda.sei.ms.shared.domain.metrics.DurationStat;
import andromeda.sei.ms.shared.domain.metrics.Percentiles;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Upserts one {@code sprint_metrics} document per sprint of the project (sprint-metrics).
 *
 * <p>Velocity follows the single definition of {@link EffortBearingLevelResolver#summarize}: the
 * effective effort of the sprint's deliverable units in {@code DONE} — the same number the
 * developer, team and strategic dashboards report. It no longer reads the legacy
 * {@code storyPoints} field. Durations are p50/p90 over completed units; the {@code avg*}
 * fields are kept, deprecated, for one release.
 *
 * <p>An iteration shared by several teams gets one document, on its canonical twin
 * ({@link SharedIterations}): the other twins hold no work items and used to get a zeroed document.
 */
@Service
@RequiredArgsConstructor
public class CalculateSprintMetricsUseCase {

    private final WorkItemRepository workItemRepository;
    private final SprintRepository sprintRepository;
    private final SprintMetricsRepository sprintMetricsRepository;
    private final EffortBearingLevelResolver effortBearingLevelResolver;

    public void execute(String organizationId, String projectId) {
        List<Sprint> sprints = SharedIterations.distinct(
                sprintRepository.findAllByOrganizationIdAndProjectId(organizationId, projectId));
        List<WorkItem> allItems = workItemRepository.findAllByOrganizationIdAndProjectId(organizationId, projectId)
                .stream().filter(WorkItem::isActive).toList();

        Map<String, List<WorkItem>> itemsBySprintId = allItems.stream()
                .filter(item -> item.getSprintId() != null)
                .collect(Collectors.groupingBy(WorkItem::getSprintId));

        for (Sprint sprint : sprints) {
            List<WorkItem> sprintItems = itemsBySprintId.getOrDefault(sprint.getId(), List.of());
            sprintMetricsRepository.save(computeMetrics(organizationId, projectId, sprint, sprintItems));
        }
    }

    private SprintMetrics computeMetrics(String organizationId, String projectId,
                                         Sprint sprint, List<WorkItem> items) {
        EffortBearingLevelResolver.EffortSummary summary = effortBearingLevelResolver.summarize(items);
        List<WorkItem> done = summary.doneUnits();
        int itemsWithEffort = (int) summary.units().stream()
                .filter(unit -> summary.scope().effectiveEffort(unit) != null)
                .count();

        SprintMetrics metrics = new SprintMetrics(
                null, organizationId, projectId, sprint.getId(),
                WorkItem.Source.AZURE_DEVOPS, summary.deliveredOrZero(),
                average(done, WorkItem::getCycleTimeHours), average(done, WorkItem::getLeadTimeHours),
                done.size(), summary.units().size(), itemsWithEffort,
                Instant.now(), true, null, null, null
        );
        DurationStat cycle = Percentiles.of(done.stream().map(WorkItem::getCycleTimeHours).toList());
        DurationStat lead = Percentiles.of(done.stream().map(WorkItem::getLeadTimeHours).toList());
        metrics.setPlannedEffort(summary.plannedOrZero());
        metrics.setPlannedComplexity(summary.plannedComplexity());
        metrics.setDeliveredComplexity(summary.deliveredComplexity());
        metrics.setCycleTimeP50(cycle.p50());
        metrics.setCycleTimeP90(cycle.p90());
        metrics.setLeadTimeP50(lead.p50());
        metrics.setLeadTimeP90(lead.p90());
        return metrics;
    }

    /** Deprecated average, kept for one release next to the percentiles. */
    private Double average(List<WorkItem> items, Function<WorkItem, Double> metric) {
        OptionalDouble avg = items.stream().map(metric).filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue).average();
        return avg.isPresent() ? avg.getAsDouble() : null;
    }
}
