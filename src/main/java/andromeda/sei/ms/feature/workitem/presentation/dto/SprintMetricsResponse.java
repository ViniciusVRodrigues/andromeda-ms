package andromeda.sei.ms.feature.workitem.presentation.dto;

import andromeda.sei.ms.feature.workitem.domain.Sprint;

import java.time.Instant;

public record SprintMetricsResponse(
        String sprintId,
        String sprintName,
        Instant startDate,
        Instant endDate,
        Sprint.TimeFrame timeFrame,
        Double velocity,
        Double avgCycleTime,
        Double avgLeadTime,
        Integer completedItemCount,
        Integer totalItemCount,
        Integer itemsWithPoints,
        Instant calculatedAt
) {}
