package andromeda.sei.ms.feature.dashboard.application.dto.v2;

import andromeda.sei.ms.shared.domain.metrics.DurationStat;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Project roadmap and backlog v2 of a period ({@code buildRoadmap} of the v2 preview), without the
 * area breakdown (AreaPath is not stored).
 *
 * <p>Effort is the effective effort ({@code EffortBearingLevelResolver}: the item's own value,
 * else the sum of its children). Item statuses are the resolved categories ({@code TODO},
 * {@code IN_PROGRESS}, {@code DONE}).
 */
public record ProjectRoadmapV2Output(
        Period period,
        Config config,
        List<EpicRow> epics,
        List<FeatureRow> features,
        Backlog backlog,
        List<TagShare> deliveredByTag,
        Calibration calibration,
        Instant asOf) {

    public record Period(int days, String start, String end) {}

    /**
     * {@code effortField}: Azure field mapped to effort (the hours logged on Tasks) on the project's binding,
     * {@code complexityField}: the one mapped to complexity. Without a mapping they are the defaults
     * ({@code Microsoft.VSTS.Scheduling.CompletedWork} and {@code Microsoft.VSTS.Scheduling.StoryPoints}),
     * {@code null} without an Azure binding. A per-type mapping is reported when no wildcard exists.
     */
    public record Config(String effortField, EffortLevel effortBearingLevel, String complexityField) {}

    /** {@code configured} is always {@code AUTO} today (no setting pins a level); {@code inferred} from the data. */
    public record EffortLevel(String configured, String inferred) {}

    /**
     * Progress of a set of children by the hierarchy.
     *
     * <ul>
     *   <li>{@code effortTotal}/{@code effortDone}: {@code null} when no
     *       child carries effort.</li>
     *   <li>{@code weeklyPace}: effort completed in the last 8 weeks ÷ 8, {@code null} when 0.</li>
     *   <li>{@code forecastWeeks}: remaining effort ÷ pace; 0 when nothing remains; {@code null}
     *       without effort or without recent pace.</li>
     *   <li>{@code lastActivityAt}: latest Azure change ({@code externalUpdatedAt}, else creation) of
     *       a child; {@code null} without children.</li>
     *   <li>{@code complexityTotal}/{@code complexityDone}: the same in points of complexity (null when no child
     *       carries it); {@code pointsPerWeek}: complexity completed in the last 8 weeks ÷ 8, null when 0
     *       (complexity-effort D1).</li>
     *   <li>{@code complexityDoneInPeriod}: complexity of the units completed in the period; null when no child carries complexity.</li>
     * </ul>
     */
    public record Progress(Double effortTotal, Double effortDone, Double pctDone, int itemsTotal, int itemsDone,
                           int unestimated, Double weeklyPace, Double forecastWeeks,
                           Instant lastActivityAt, Double complexityTotal, Double complexityDone,
                           Double pointsPerWeek, Double complexityDoneInPeriod) {}

    /** Children: the work of its features plus items linked straight to the epic. */
    public record EpicRow(String externalId, String title, String status, List<String> tags, int featureCount,
                          int featuresDone, @JsonUnwrapped Progress progress) {}

    public record FeatureRow(String externalId, String title, String parentExternalId, String epic, String status,
                             @JsonUnwrapped Progress progress) {}

    /**
     * Backlog = active items that are neither epic nor feature, not done, outside any sprint or in a
     * future one. Staleness by the last Azure change; items without any date are left out of
     * {@code staleness}, {@code byPriority[].stale} and {@code forgotten}.
     */
    public record Backlog(int count, Double unestimatedPct, Double withoutParentPct,
                          List<StaleBand> staleness, List<PriorityRow> byPriority, List<ForgottenItem> forgotten,
                          List<TagShare> byTag, Double points) {}

    /** {@code [minDays, maxDays)} since the last change; {@code maxDays} {@code null} on the last band. */
    public record StaleBand(String label, int minDays, Integer maxDays, int count) {}

    /** Priorities 1–4; an item whose priority is not one of them counts in none. */
    public record PriorityRow(int priority, int count, int stale) {}

    public record ForgottenItem(String externalId, String title, String type, Integer priority, Double effort,
                                Double daysWithoutChange, List<String> tags, Double complexity,
                                Double effortRolledUp) {}

    /** {@code points}: effective complexity; items without it adding 0. */
    public record TagShare(String tag, int count, double points) {}

    public record Calibration(List<EffortRow> byEffort, List<ComplexityRow> byComplexity,
                              List<ManualOverride> overrides, Double overridePct, List<Outlier> outliers) {}

    /**
     * Hours logged on the Tasks of the units of a complexity band (nearest-rank quartiles, the same rule as the
     * other percentiles); every figure {@code null} and {@code n = 0} without a sample.
     */
    public record EffortHours(Double p25, Double p50, Double p75, int n) {}

    /**
     * A unit whose logged hours are above twice or below half the median of its band (at most 5, largest
     * deviation first); {@code url} is the item in Azure DevOps, {@code null} when unknown.
     */
    public record Outlier(String externalId, String title, String type, Double complexity, Double effortHours,
                          String url) {}

    /**
     * Units completed in the period with this effort value (the item's own field). {@code label} is just the
     * number: the unit is the ui's to format (complexity-effort D7).
     */
    public record EffortRow(String label, double effort, int count, DurationStat cycleTimeHours,
                            BigDecimal aiCostUsd, Long aiTokens, int itemsWithAi) {}

    /**
     * {@code aiCostUsd}/{@code aiTokens}: VALIDATED allocation to the units of the band (same base as the
     * item's {@code aiCostUsd}); {@code null} and {@code itemsWithAi = 0} when none of them has allocation.
     * {@code label} is just the number (D7); {@code avgEffort}: the average Task hours logged on the band's units.
     */
    public record ComplexityRow(String label, double complexity, int count, DurationStat cycleTimeHours,
                                Double avgEffort, BigDecimal aiCostUsd, Long aiTokens, int itemsWithAi,
                                EffortHours effortHours, int unitsWithoutEffort) {}

    /** {@code field}: {@code "Esforço"} or {@code "Complexidade"}; {@code by}: the user's name. */
    public record ManualOverride(String externalId, String title, String field, Double value, String by, Instant at) {}
}
