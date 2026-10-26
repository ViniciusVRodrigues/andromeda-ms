package andromeda.sei.ms.feature.dashboard.application.dto.v2;

import andromeda.sei.ms.feature.dashboard.application.dto.v2.DashboardV2Parts.AiDay;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.DashboardV2Parts.AiModel;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.DashboardV2Parts.AllocationSummary;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.DashboardV2Parts.BurndownDay;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.DashboardV2Parts.CommitDay;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.DashboardV2Parts.ItemView;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.DashboardV2Parts.PullRequestView;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.DashboardV2Parts.SprintMeta;
import andromeda.sei.ms.feature.metrics.domain.DeliveryMetrics;
import andromeda.sei.ms.shared.domain.metrics.DurationStat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Team dashboard v2 of a project and sprint ({@code buildTeam}).
 *
 * <p><b>AI cost basis:</b> {@code kpis.aiCostUsd}, {@code history[].aiCostUsd}, {@code ai.byDay} and
 * {@code ai.byModel} are the cost <b>allocated to the project</b> (validated allocations weighted by
 * the project's split share, {@code ProjectAiCostCalculator}) — they reconcile with each other. Cost
 * not allocated yet is never added to them: it is reported apart in
 * {@code kpis.unallocatedAiCostUsd} / {@code ai.unallocatedCostUsd}.
 *
 * <p>No sprint in the project yet: {@code sprint}, {@code kpis} and {@code previous} are
 * {@code null} and the lists are empty ({@link #withoutSprint}).
 */
public record TeamDashboardV2Output(
        SprintMeta sprint,
        TeamInfo team,
        Kpis kpis,
        SprintStats previous,
        List<BurndownDay> burndown,
        List<StatusByType> statusByType,
        List<ItemView> agingItems,
        Double p85CycleHours,
        List<Member> members,
        List<PullRequestView> stalePrs,
        List<RepoHealth> repos,
        List<CommitDay> commitsByDay,
        Ai ai,
        List<HistoryPoint> history,
        Instant asOf) {

    public static TeamDashboardV2Output withoutSprint(TeamInfo team, Instant asOf) {
        return new TeamDashboardV2Output(null, team, null, null, List.of(), List.of(), List.of(), null, List.of(),
                List.of(), List.of(), List.of(), Ai.EMPTY, List.of(), asOf);
    }

    /** {@code name}: the Azure team of the sprint, falling back to the project name. */
    public record TeamInfo(String name, int memberCount) {}

    /**
     * Delivery of one sprint for the team. Velocity ({@code spDelivered}) and planned effort follow
     * the single definition ({@code EffortBearingLevelResolver.summarize}); {@code aiCostPerSp} =
     * allocated cost / delivered effort.
     */
    public record SprintStats(Double spPlanned, Double spDelivered, int itemsDone, int itemsTotal,
                              int itemsWithPoints, int unassigned, DurationStat cycleTimeHours,
                              DurationStat leadTimeHours, BigDecimal aiCostUsd, BigDecimal aiCostPerSp,
                              DurationStat timeToFirstReviewHours, int prsMerged) {}

    /**
     * {@link SprintStats} of the selected sprint; {@code openPrs}/{@code stalePrs} are the current
     * state, so only for the current sprint ({@code null} for a past one).
     */
    public record Kpis(Double spPlanned, Double spDelivered, int itemsDone, int itemsTotal, int itemsWithPoints,
                       int unassigned, DurationStat cycleTimeHours, DurationStat leadTimeHours,
                       BigDecimal aiCostUsd, BigDecimal aiCostPerSp, DurationStat timeToFirstReviewHours,
                       int prsMerged, Integer openPrs, Integer stalePrs, BigDecimal unallocatedAiCostUsd) {}

    public record StatusByType(String type, int todo, int inProgress, int done) {}

    /**
     * A project member (or assignee of a sprint item). PR and review counts are {@code null} when
     * the person has no provider identity linked; {@code aiCostUsd} (allocated to the project) is
     * {@code null} when the person has no connector token.
     */
    public record Member(String userId, String name, @JsonProperty("isMe") boolean isMe, boolean githubLinked,
                         boolean vcsLinked, int itemsDone, int itemsTotal, Double spPlanned, Double spDelivered,
                         int wip, DurationStat cycleTimeHours, Integer prsOpened, Integer prsMerged,
                         Integer reviewsGiven, BigDecimal aiCostUsd, Double avgTokensPerSp) {}

    /**
     * Health of one bound repository over the sprint window ({@code ListProjectRepositoriesUseCase}
     * definitions): open/stale are the current state; {@code delivery} is the DORA calculator.
     */
    public record RepoHealth(String repositoryId, String displayName, String provider, long openPrs, long stalePrs,
                             int prsMerged, DurationStat prCycleTimeHours, DurationStat timeToFirstReviewHours,
                             Double reviewedWithinSlaPercent, DeliveryMetrics delivery, int commits) {}

    public record Ai(List<AiDay> byDay, List<AiModel> byModel, AllocationSummary allocation, int membersWithUsage,
                     BigDecimal unallocatedCostUsd) {
        public static final Ai EMPTY = new Ai(List.of(), List.of(), new AllocationSummary(0, 0, 0), 0, null);
    }

    /** {@code predictabilityPct} = delivered / planned × 100, {@code null} without planned effort. */
    public record HistoryPoint(String sprintId, String label, String name,
                               @JsonProperty("isSelected") boolean isSelected,
                               @JsonProperty("isCurrent") boolean isCurrent,
                               Double spPlanned, Double spDelivered, Double predictabilityPct,
                               DurationStat cycleTimeHours, BigDecimal aiCostUsd, BigDecimal aiCostPerSp,
                               Long aiTokens) {}
}
