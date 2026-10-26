package andromeda.sei.ms.feature.workitem.application.dto;

import andromeda.sei.ms.feature.workitem.domain.Sprint;

import java.time.Instant;

public record SprintMetricsOutput(
        String sprintId,
        String sprintName,
        Instant startDate,
        Instant endDate,
        Sprint.TimeFrame timeFrame,
        Double velocity,
        // deprecated since metrics-correctness: use cycleTimeP50/cycleTimeP90
        Double avgCycleTime,
        // deprecated since metrics-correctness: use leadTimeP50/leadTimeP90
        Double avgLeadTime,
        Double plannedEffort,
        Double plannedComplexity,
        Double deliveredComplexity,
        Double cycleTimeP50,
        Double cycleTimeP90,
        Double leadTimeP50,
        Double leadTimeP90,
        Integer completedItemCount,
        Integer totalItemCount,
        Integer itemsWithPoints,
        Instant calculatedAt
) {}
