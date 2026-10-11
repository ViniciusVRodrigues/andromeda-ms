package andromeda.sei.ms.feature.dashboard.application.dto.v2;

import andromeda.sei.ms.shared.domain.metrics.DurationStat;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

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
     * {@code effortField}: Azure field mapped to effort on the project's binding (the default
     * {@code Microsoft.VSTS.Scheduling.StoryPoints} without a mapping; {@code null} without an
     * Azure binding).
     */
    public record Config(String effortField, EffortLevel effortBearingLevel) {}

    /** {@code configured} is always {@code AUTO} today (no setting pins a level); {@code inferred} from the data. */
    public record EffortLevel(String configured, String inferred) {}

    /**
     * Progress of a set of children by the hierarchy.
     *
     * <ul>
     *   <li>{@code effortTotal}/{@code effortDone}/{@code spDoneInPeriod}: {@code null} when no
     *       child carries effort.</li>
     *   <li>{@code weeklyPace}: effort completed in the last 8 weeks ÷ 8, {@code null} when 0.</li>
     *   <li>{@code forecastWeeks}: remaining effort ÷ pace; 0 when nothing remains; {@code null}
     *       without effort or without recent pace.</li>
     *   <li>{@code lastActivityAt}: latest Azure change ({@code externalUpdatedAt}, else creation) of
     *       a child; {@code null} without children.</li>
     * </ul>
     */
    public record Progress(Double effortTotal, Double effortDone, Double pctDone, int itemsTotal, int itemsDone,
                           int unestimated, Double spDoneInPeriod, Double weeklyPace, Double forecastWeeks,
                           Instant lastActivityAt) {}

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
    public record Backlog(int count, Double sp, Double unestimatedPct, Double withoutParentPct,
                          List<StaleBand> staleness, List<PriorityRow> byPriority, List<ForgottenItem> forgotten,
                          List<TagShare> byTag) {}

    /** {@code [minDays, maxDays)} since the last change; {@code maxDays} {@code null} on the last band. */
    public record StaleBand(String label, int minDays, Integer maxDays, int count) {}

    /** Priorities 1–4; an item whose priority is not one of them counts in none. */
    public record PriorityRow(int priority, int count, int stale) {}

    public record ForgottenItem(String externalId, String title, String type, Integer priority, Double effort,
                                Double daysWithoutChange, List<String> tags) {}

    /** {@code sp}: effective effort, items without effort adding 0. */
    public record TagShare(String tag, int count, double sp) {}

    public record Calibration(List<EffortRow> byEffort, List<ComplexityRow> byComplexity,
                              List<ManualOverride> overrides, Double overridePct) {}

    /** Units completed in the period with this effort value (the item's own field). */
    public record EffortRow(String label, double effort, int count, DurationStat cycleTimeHours) {}

    public record ComplexityRow(String label, double complexity, int count, DurationStat cycleTimeHours,
                                Double avgEffort) {}

    /** {@code field}: {@code "Esforço"} or {@code "Complexidade"}; {@code by}: the user's name. */
    public record ManualOverride(String externalId, String title, String field, Double value, String by, Instant at) {}
}
