package andromeda.sei.ms.feature.workitem.application.usecase;

import andromeda.sei.ms.feature.workitem.application.dto.SprintMetricsOutput;
import andromeda.sei.ms.feature.workitem.domain.SharedIterations;
import andromeda.sei.ms.feature.workitem.domain.Sprint;
import andromeda.sei.ms.feature.workitem.domain.SprintMetrics;
import andromeda.sei.ms.feature.workitem.domain.SprintMetricsRepository;
import andromeda.sei.ms.feature.workitem.domain.SprintRepository;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GetSprintMetricsUseCase {

    private final SprintMetricsRepository sprintMetricsRepository;
    private final SprintRepository sprintRepository;

    public List<SprintMetricsOutput> execute(String projectId) {
        String organizationId = TenantContextHolder.get();

        List<SprintMetrics> metrics = sprintMetricsRepository
                .findAllByOrganizationIdAndProjectId(organizationId, projectId);

        List<Sprint> sprints = sprintRepository
                .findAllByOrganizationIdAndProjectId(organizationId, projectId);

        Map<String, Sprint> sprintById = sprints.stream()
                .collect(Collectors.toMap(Sprint::getId, s -> s));
        // Documents computed before shared iterations were deduplicated may exist for non-canonical
        // twins (all zeros): hide them so each iteration is listed once.
        Set<String> canonicalIds = SharedIterations.distinct(sprints).stream()
                .map(Sprint::getId).collect(Collectors.toSet());

        return metrics.stream()
                .filter(m -> !sprintById.containsKey(m.getSprintId()) || canonicalIds.contains(m.getSprintId()))
                .map(m -> {
                    Sprint sprint = sprintById.get(m.getSprintId());
                    return new SprintMetricsOutput(
                            m.getSprintId(),
                            sprint != null ? sprint.getName() : null,
                            sprint != null ? sprint.getStartDate() : null,
                            sprint != null ? sprint.getEndDate() : null,
                            sprint != null ? sprint.getTimeFrame() : null,
                            m.getVelocity(),
                            m.getAvgCycleTime(),
                            m.getAvgLeadTime(),
                            m.getPlannedEffort(),
                            m.getPlannedComplexity(),
                            m.getDeliveredComplexity(),
                            m.getCycleTimeP50(),
                            m.getCycleTimeP90(),
                            m.getLeadTimeP50(),
                            m.getLeadTimeP90(),
                            m.getCompletedItemCount(),
                            m.getTotalItemCount(),
                            m.getItemsWithPoints(),
                            m.getCalculatedAt()
                    );
                })
                .sorted(Comparator.comparing(SprintMetricsOutput::startDate,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }
}
