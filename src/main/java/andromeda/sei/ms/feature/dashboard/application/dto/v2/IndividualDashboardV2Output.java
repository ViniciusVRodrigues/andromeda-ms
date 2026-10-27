package andromeda.sei.ms.feature.dashboard.application.dto.v2;

import andromeda.sei.ms.feature.dashboard.application.dto.v2.DashboardV2Parts.AiDay;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.DashboardV2Parts.AiModel;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.DashboardV2Parts.AllocationSummary;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.DashboardV2Parts.BurndownDay;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.DashboardV2Parts.CommitDay;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.DashboardV2Parts.ItemView;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.DashboardV2Parts.PullRequestView;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.DashboardV2Parts.SprintMeta;
import andromeda.sei.ms.shared.domain.metrics.DurationStat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Individual dashboard v2 of the caller in one project and sprint ({@code buildIndividual}). The
 * person is always the authenticated user. Durations are {@link DurationStat} {p50, p90, n} in
 * hours; {@code aiCostUsd}/{@code aiTokens} are the person's AI usage in the sprint span (any
 * project, the developer-dashboard basis), {@code null} when there is no usage.
 *
 * <p>No sprint in the project yet: {@code sprint}, {@code kpis} and {@code previous} are
 * {@code null} and the lists are empty ({@link #withoutSprint}).
 */
public record IndividualDashboardV2Output(
        SprintMeta sprint,
        Person person,
        Kpis kpis,
        SprintStats previous,
        List<BurndownDay> burndown,
        List<ItemView> items,
        List<PullRequestView> pullRequests,
        ReviewsGiven reviewsGiven,
        List<CommitDay> commitsByDay,
        Ai ai,
        List<HistoryPoint> history,
        Instant asOf) {

    public static IndividualDashboardV2Output withoutSprint(Person person, Instant asOf) {
        return new IndividualDashboardV2Output(null, person, null, null, List.of(), List.of(), List.of(),
                ReviewsGiven.NONE, List.of(), Ai.EMPTY, List.of(), asOf);
    }

    /**
     * {@code githubLinked}: the user has a GitHub account linked; {@code vcsLinked}: any provider
     * identity (GitHub or Azure DevOps); {@code aiConnected}: holds an active connector token.
     */
    public record Person(String id, String name, boolean githubLinked, boolean vcsLinked, boolean aiConnected) {}

    /** Delivery of one sprint for the person (the {@code previous} sprint and each history point). */
    public record SprintStats(int itemsDone, int itemsTotal,
                              DurationStat cycleTimeHours, DurationStat leadTimeHours, int prsMerged,
                              DurationStat timeToFirstReviewHours, BigDecimal aiCostUsd, Long aiTokens,
                              Double complexityPlanned, Double complexityDelivered,
                              Double effortLogged, Double avgTokensPerPoint, Double avgTokensPerHour,
                              int unitsWithoutComplexity) {}

    /**
     * {@link SprintStats} of the selected sprint plus the person's open pull requests (any age) — a
     * current state, so {@code null} for a past sprint (like the team view's {@code openPrs}).
     */
    public record Kpis(int itemsDone, int itemsTotal,
                       DurationStat cycleTimeHours, DurationStat leadTimeHours, int prsMerged,
                       DurationStat timeToFirstReviewHours, BigDecimal aiCostUsd, Long aiTokens,
                       Integer prsOpen, Double complexityPlanned, Double complexityDelivered,
                       Double effortLogged, Double avgTokensPerPoint, Double avgTokensPerHour,
                       int unitsWithoutComplexity) {}

    /** Reviews the person submitted on the project's pull requests in the sprint (bots excluded). */
    public record ReviewsGiven(int total, int approved, int changesRequested, int commented, int rejected) {
        public static final ReviewsGiven NONE = new ReviewsGiven(0, 0, 0, 0, 0);
    }

    public record Ai(List<AiDay> byDay, List<AiModel> byModel, AllocationSummary allocation,
                     List<PendingAllocation> pendingAllocations, List<UnallocatedDay> unallocatedDayList,
                     List<String> editors) {
        public static final Ai EMPTY = new Ai(List.of(), List.of(), new AllocationSummary(0, 0, 0), List.of(),
                List.of(), List.of());
    }

    /**
     * A day whose automatic split awaits validation. {@code day} is the UTC date, {@code cost} the
     * USD cost of the day's usage and {@code percentage} (in {@link PendingSplit}) a 0-1 fraction.
     */
    public record PendingAllocation(String allocationId, String day, String label, long tokens,
                                    BigDecimal cost, String suggestionSource, List<PendingSplit> splits) {}

    public record PendingSplit(String workItemId, String externalId, String title, Double percentage) {}

    /** A day of the window with usage but neither a validated allocation nor a pending suggestion. */
    public record UnallocatedDay(String day, long tokens, BigDecimal cost) {}

    public record HistoryPoint(String sprintId, String label, String name,
                               @JsonProperty("isSelected") boolean isSelected,
                               @JsonProperty("isCurrent") boolean isCurrent,
                               DurationStat cycleTimeHours,
                               BigDecimal aiCostUsd, Long aiTokens, Double complexityPlanned,
                               Double complexityDelivered, Double effortLogged) {}
}
