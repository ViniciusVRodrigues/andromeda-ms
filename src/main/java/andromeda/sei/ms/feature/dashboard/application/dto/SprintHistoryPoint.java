package andromeda.sei.ms.feature.dashboard.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One entry of the developer dashboard's sprint history: the sprint's own
 * delivery numbers, computed with the same effort-bearing-level resolution the
 * current-sprint metrics use, so bars are comparable across sprints.
 * Null metrics mean "no data" (never zero-filled) except spPlanned/spDelivered,
 * which are 0 for a sprint whose items carry no effort. {@code avgCycleTimeHours} is deprecated
 * in favour of {@code cycleTimeP50}.
 */
public record SprintHistoryPoint(
        String sprintId,
        String name,
        LocalDate startDate,
        LocalDate endDate,
        Double spPlanned,
        Double spDelivered,
        Double avgCycleTimeHours,
        Long totalTokens,
        BigDecimal totalCostUsd,
        Double cycleTimeP50
) {
    public SprintHistoryPoint(String sprintId, String name, LocalDate startDate, LocalDate endDate,
                              Double spPlanned, Double spDelivered, Double avgCycleTimeHours,
                              Long totalTokens, BigDecimal totalCostUsd) {
        this(sprintId, name, startDate, endDate, spPlanned, spDelivered, avgCycleTimeHours, totalTokens,
                totalCostUsd, null);
    }
}
