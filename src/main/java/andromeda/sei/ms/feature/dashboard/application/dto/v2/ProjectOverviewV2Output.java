package andromeda.sei.ms.feature.dashboard.application.dto.v2;

import andromeda.sei.ms.shared.domain.metrics.DurationStat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Project overview v2 of a period ({@code buildProject} of the v2 preview).
 *
 * <p><b>AI cost basis:</b> {@code kpis.aiCostUsd}, {@code sprints[].aiCostUsd} and
 * {@code ai.costByBucket} are the cost <b>allocated to the project</b> (validated allocations
 * weighted by the project's split share). {@code ai.subscription.consumptionUsd} is a different
 * question — what the members' usage cost in total, against the seats — so it is the members'
 * whole usage, allocated or not.
 *
 * <p>Series ({@code throughput}, {@code flow}, {@code quality}, {@code prFlow},
 * {@code ai.costByBucket}) have one row per bucket of {@code period.granularity}, even without
 * data: counts are then 0, ratios and durations {@code null}.
 */
public record ProjectOverviewV2Output(
        ProjectInfo project,
        Period period,
        PeriodStats kpis,
        PeriodStats previous,
        Dora dora,
        List<ThroughputRow> throughput,
        List<SprintRow> sprints,
        List<FlowRow> flow,
        List<QualityRow> quality,
        List<PrFlowRow> prFlow,
        List<RepoRow> repos,
        Ai ai,
        People people,
        DataQuality dataQuality,
        Instant asOf) {

    /** {@code team}: the Azure team of the latest sprint, {@code null} without sprints. */
    public record ProjectInfo(String id, String name, String team, int memberCount, List<String> repos) {}

    /** Days {@code yyyy-MM-dd}; {@code granularity} {@code "semana"} or {@code "mês"}. */
    public record Period(int days, String start, String end, String previousStart, String previousEnd,
                         String granularity) {}

    /**
     * Delivery of the project in one period.
     *
     * <ul>
     *   <li>{@code throughput}: STORY, BUG and TASK items completed (DONE) in the period;
     *       {@code bugSharePct} is the BUG share among them.</li>
     *   <li>{@code spDelivered}, {@code leadTimeHours}, {@code cycleTimeHours}: deliverable units
     *       completed in the period (the single velocity definition), epics and features excluded.</li>
     *   <li>{@code predictabilityPct}: average of delivered ÷ planned of the past sprints that ended
     *       in the period.</li>
     *   <li>{@code deployFreqPerWeek}, {@code cfrPct}: the delivery-metrics proxies (see {@code dora}).</li>
     *   <li>{@code stalePrs}: current state, so {@code null} in {@code previous}.</li>
     * </ul>
     */
    public record PeriodStats(int throughput, Double deployFreqPerWeek, Double spDelivered, Double predictabilityPct,
                              DurationStat leadTimeHours, DurationStat cycleTimeHours, Double cfrPct,
                              Double bugSharePct, BigDecimal aiCostUsd, BigDecimal aiCostPerSp, int prsMerged,
                              DurationStat prCycleTimeHours, DurationStat timeToFirstReviewHours, Integer stalePrs,
                              int commitsCount) {}

    /**
     * Delivery metrics of the period ({@code DeliveryMetricsCalculator}): grades are the
     * {@code PerformanceClass} names ({@code ELITE}, {@code HIGH}, {@code MEDIUM}, {@code LOW});
     * {@code method} says which proxy produced them ({@code VCS_PROXY}, {@code BOARDS_PROXY},
     * {@code UNAVAILABLE}).
     */
    public record Dora(String method, LeadTime leadTime, DeployFrequency deployFrequency,
                       ChangeFailureRate changeFailureRate) {}

    public record LeadTime(DurationStat hours, String grade) {}

    public record DeployFrequency(Double perWeek, String grade, boolean estimated) {}

    public record ChangeFailureRate(Double pct, String grade, boolean estimated) {}

    public record ThroughputRow(String start, String label, @JsonProperty("STORY") int story,
                                @JsonProperty("BUG") int bug, @JsonProperty("TASK") int task) {}

    /** A past sprint that ended in the period: committed × delivered and the AI cost per SP. */
    public record SprintRow(String sprintId, String label, String name, Double spPlanned, Double spDelivered,
                            Double predictabilityPct, BigDecimal aiCostUsd, BigDecimal aiCostPerSp) {}

    public record FlowRow(String start, String label, DurationStat leadTimeHours, DurationStat cycleTimeHours) {}

    public record QualityRow(String start, String label, Double bugSharePct) {}

    public record PrFlowRow(String start, String label, int opened, int merged, DurationStat timeToFirstReviewHours) {}

    /**
     * Health of one bound repository: commits, merges, PR cycle time and time to first review in the
     * period; open/stalled pull requests now; {@code cfrPct} = delivery change fail rate in the period.
     */
    public record RepoRow(String repositoryId, String repo, String provider, int commits, int prsMerged,
                          DurationStat prCycleTimeHours, DurationStat timeToFirstReviewHours, int openPrs,
                          int stalePrs, Double cfrPct, Boolean syncEnabled, Instant lastSyncAt) {}

    /** {@code byModel}: allocated cost per model in the bucket, every model of {@code ai.models} present. */
    public record CostBucket(String start, String label, Map<String, BigDecimal> byModel) {}

    public record Adoption(int activeUsers, int members) {}

    /**
     * {@code subscriptionUsd} = {@code perUserMonthUsd × members × days / 30}; both {@code null}
     * when the organization has no subscription cost configured.
     */
    public record Subscription(BigDecimal consumptionUsd, BigDecimal subscriptionUsd, BigDecimal perUserMonthUsd) {}

    public record Ai(List<String> models, List<CostBucket> costByBucket, Adoption adoption, Subscription subscription,
                     Double tokensPerSp) {}

    public record People(int members, int azureLinked, int githubLinked, int aiConnected) {}

    /**
     * Coverage: deliverable units of the period's sprints with effort, pull requests opened in the
     * period that cite a work item, and (person, day) pairs of AI usage already allocated.
     */
    public record DataQuality(Double itemsWithEstimatePct, Double prsLinkedPct, Double aiDaysAllocatedPct) {}
}
