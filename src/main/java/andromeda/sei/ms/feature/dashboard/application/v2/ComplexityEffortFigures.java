package andromeda.sei.ms.feature.dashboard.application.v2;

import andromeda.sei.ms.feature.workitem.application.usecase.EffortBearingLevelResolver.EffortSummary;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;

import java.util.List;

/**
 * The complexity/effort figures of one scope (a sprint, a person, a project window) that every v2 payload
 * repeats (complexity-effort D1-D3), read from the single {@link EffortSummary}.
 *
 * @param complexityPlanned    effective complexity of the story-tier units; {@code null} when none carries it
 * @param complexityDelivered  that of the delivered units; {@code 0} when planned exists but nothing is done
 * @param effortLogged         Task hours of the delivered units; {@code null} when none
 * @param avgTokensPerPoint    average of the units' {@code tokensPerPoint}
 * @param avgTokensPerHour     average of the units' {@code tokensPerHour}
 * @param unitsWithoutComplexity story-tier units with no complexity
 * @param itemsWithComplexity  story-tier units with complexity
 */
public record ComplexityEffortFigures(Double complexityPlanned, Double complexityDelivered, Double effortLogged,
                                      Double avgTokensPerPoint, Double avgTokensPerHour,
                                      int unitsWithoutComplexity, int itemsWithComplexity) {

    public static final ComplexityEffortFigures EMPTY =
            new ComplexityEffortFigures(null, null, null, null, null, 0, 0);

    public static ComplexityEffortFigures of(EffortSummary summary) {
        List<WorkItem> pool = summary.complexityUnits().isEmpty() ? summary.units() : summary.complexityUnits();
        return new ComplexityEffortFigures(summary.plannedComplexity(),
                DashboardV2Views.deliveredOf(summary.plannedComplexity(), summary.deliveredComplexity()),
                summary.effortLogged(),
                DashboardV2Views.averageOrNull(pool.stream().map(WorkItem::getTokensPerPoint).toList()),
                DashboardV2Views.averageOrNull(pool.stream().map(WorkItem::getTokensPerHour).toList()),
                summary.unitsWithoutComplexity(),
                summary.complexityUnits().size() - summary.unitsWithoutComplexity());
    }

    /** The same figures for the units of one assignee inside a team scope. */
    public static ComplexityEffortFigures ofAssignee(EffortSummary summary, String userId) {
        List<WorkItem> pool = (summary.complexityUnits().isEmpty() ? summary.units() : summary.complexityUnits())
                .stream().filter(u -> userId.equals(u.getAssigneeUserId())).toList();
        List<WorkItem> done = pool.stream()
                .filter(u -> WorkItem.ResolvedStatus.DONE.equals(u.getResolvedStatus())).toList();
        var scope = summary.scope();
        Double planned = scope.totalComplexity(pool);
        int without = (int) pool.stream().filter(u -> scope.effectiveComplexity(u) == null).count();
        return new ComplexityEffortFigures(planned, DashboardV2Views.deliveredOf(planned, scope.totalComplexity(done)),
                scope.totalTaskEffort(done),
                DashboardV2Views.averageOrNull(pool.stream().map(WorkItem::getTokensPerPoint).toList()),
                DashboardV2Views.averageOrNull(pool.stream().map(WorkItem::getTokensPerHour).toList()),
                without, pool.size() - without);
    }
}
