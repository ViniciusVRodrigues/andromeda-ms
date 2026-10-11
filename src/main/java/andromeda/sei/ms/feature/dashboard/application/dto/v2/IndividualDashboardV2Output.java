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
        Fluency fluency,
        List<HistoryPoint> history,
        Instant asOf) {

    public static IndividualDashboardV2Output withoutSprint(Person person, Fluency fluency, Instant asOf) {
        return new IndividualDashboardV2Output(null, person, null, null, List.of(), List.of(), List.of(),
                ReviewsGiven.NONE, List.of(), Ai.EMPTY, fluency, List.of(), asOf);
    }

    /**
     * {@code githubLinked}: the user has a GitHub account linked; {@code vcsLinked}: any provider
     * identity (GitHub or Azure DevOps); {@code aiConnected}: holds an active connector token.
     */
    public record Person(String id, String name, boolean githubLinked, boolean vcsLinked, boolean aiConnected) {}

    /** Delivery of one sprint for the person (the {@code previous} sprint and each history point). */
    public record SprintStats(Double spPlanned, Double spDelivered, int itemsDone, int itemsTotal,
                              DurationStat cycleTimeHours, DurationStat leadTimeHours, int prsMerged,
                              DurationStat timeToFirstReviewHours, BigDecimal aiCostUsd, Long aiTokens,
                              Double avgTokensPerSp) {}

    /**
     * {@link SprintStats} of the selected sprint plus the person's open pull requests (any age) — a
     * current state, so {@code null} for a past sprint (like the team view's {@code openPrs}).
     */
    public record Kpis(Double spPlanned, Double spDelivered, int itemsDone, int itemsTotal,
                       DurationStat cycleTimeHours, DurationStat leadTimeHours, int prsMerged,
                       DurationStat timeToFirstReviewHours, BigDecimal aiCostUsd, Long aiTokens,
                       Double avgTokensPerSp, Integer prsOpen) {}

    /** Reviews the person submitted on the project's pull requests in the sprint (bots excluded). */
    public record ReviewsGiven(int total, int approved, int changesRequested, int commented, int rejected) {
        public static final ReviewsGiven NONE = new ReviewsGiven(0, 0, 0, 0, 0);
    }

    public record Ai(List<AiDay> byDay, List<AiModel> byModel, AllocationSummary allocation,
                     List<PendingAllocation> pendingAllocations, List<String> editors) {
        public static final Ai EMPTY = new Ai(List.of(), List.of(), new AllocationSummary(0, 0, 0), List.of(),
                List.of());
    }

    /** A day whose automatic split awaits validation; {@code pct} is 0–100. */
    public record PendingAllocation(String date, String label, long tokens, BigDecimal costUsd,
                                    List<PendingSplit> splits) {}

    public record PendingSplit(String externalId, String title, Double pct) {}

    /** Latest fluency score sent by the connector; {@code computedAt} as sent. */
    public record Fluency(int overallStage, String overallLabel, String computedAt, List<FluencyCategory> categories) {}

    public record FluencyCategory(String category, String icon, int stage, List<String> tips) {}

    public record HistoryPoint(String sprintId, String label, String name,
                               @JsonProperty("isSelected") boolean isSelected,
                               @JsonProperty("isCurrent") boolean isCurrent,
                               Double spPlanned, Double spDelivered, DurationStat cycleTimeHours,
                               BigDecimal aiCostUsd, Long aiTokens) {}
}
