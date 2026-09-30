package andromeda.sei.ms.feature.metrics.domain;

import andromeda.sei.ms.feature.organization.domain.RemediationRules;
import andromeda.sei.ms.shared.domain.metrics.DurationStat;
import andromeda.sei.ms.shared.domain.metrics.Percentiles;
import andromeda.sei.ms.shared.domain.metrics.Ratios;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * The single definition of the delivery metrics (delivery-metrics, design D1/D2). Pure: callers
 * load the data of their scope (project, organization as a union of projects, repository) and the
 * window; this class only counts.
 *
 * <ul>
 *   <li>{@link DeliveryMethod#VCS_PROXY} when at least one pull request was merged into a default
 *       branch in the window: CFR = remediation merges ÷ merges; deploys/week = merges ÷ weeks;
 *       change lead time = merge − first commit (or creation).</li>
 *   <li>{@link DeliveryMethod#BOARDS_PROXY} otherwise, when work items were completed: CFR =
 *       completed bugs ÷ completed items; deploys/week = completed ÷ weeks; lead time = work item
 *       lead time.</li>
 *   <li>{@link DeliveryMethod#UNAVAILABLE} when neither has data: every value and class is null.</li>
 * </ul>
 *
 * <p>The window is half-open {@code [from, to)}; weeks = {@code max(1, days / 7)}.
 */
public final class DeliveryMetricsCalculator {

    static final double DAYS_PER_WEEK = 7.0;
    private static final double MIN_WEEKS = 1.0;
    private static final double SECONDS_PER_DAY = 86_400.0;

    private static final double CFR_ELITE_MAX = 5.0;
    private static final double CFR_HIGH_MAX = 10.0;
    private static final double CFR_MEDIUM_MAX = 15.0;
    private static final double DEPLOYS_ELITE_MIN = 7.0;
    private static final double DEPLOYS_HIGH_MIN = 1.0;
    private static final double DEPLOYS_MEDIUM_MIN = 0.25;
    private static final double LEAD_ELITE_MAX_HOURS = 1.0;
    private static final double LEAD_HIGH_MAX_HOURS = 24.0;
    private static final double LEAD_MEDIUM_MAX_HOURS = 168.0;
    private static final int ROUNDING_SCALE = 100;

    private DeliveryMetricsCalculator() {}

    public static DeliveryMetrics calculate(Collection<MergedChange> changes,
                                            Collection<CompletedWorkItem> completedItems,
                                            RemediationRules rules, Instant from, Instant to) {
        double weeks = weeks(from, to);
        List<MergedChange> merged = changes == null ? List.of() : changes.stream()
                .filter(Objects::nonNull)
                .filter(c -> inWindow(c.mergedAt(), from, to) && c.isIntoDefaultBranch())
                .toList();
        if (!merged.isEmpty()) return fromChanges(merged, rules, weeks);

        List<CompletedWorkItem> completed = completedItems == null ? List.of() : completedItems.stream()
                .filter(Objects::nonNull)
                .filter(i -> inWindow(i.completedAt(), from, to))
                .toList();
        if (!completed.isEmpty()) return fromBoards(completed, weeks);

        return DeliveryMetrics.unavailable();
    }

    private static DeliveryMetrics fromChanges(List<MergedChange> merged, RemediationRules rules, double weeks) {
        int remediations = (int) merged.stream().filter(c -> RemediationClassifier.isRemediation(c, rules)).count();
        DurationStat leadTime = Percentiles.of(merged.stream()
                .filter(c -> c.leadTimeStart() != null)
                .map(c -> Ratios.hours(c.leadTimeStart(), c.mergedAt()))
                .toList());
        return build(DeliveryMethod.VCS_PROXY, Ratios.percent(remediations, merged.size()),
                merged.size() / weeks, leadTime, merged.size(), remediations);
    }

    private static DeliveryMetrics fromBoards(List<CompletedWorkItem> completed, double weeks) {
        long bugs = completed.stream().filter(CompletedWorkItem::bug).count();
        DurationStat leadTime = Percentiles.of(completed.stream().map(CompletedWorkItem::leadTimeHours).toList());
        return build(DeliveryMethod.BOARDS_PROXY, Ratios.percent(bugs, completed.size()),
                completed.size() / weeks, leadTime, 0, 0);
    }

    private static DeliveryMetrics build(DeliveryMethod method, Double cfr, double deploysPerWeek,
                                         DurationStat leadTime, int mergedPrs, int remediationPrs) {
        double deploys = Math.round(deploysPerWeek * ROUNDING_SCALE) / (double) ROUNDING_SCALE;
        return new DeliveryMetrics(method, cfr, classifyChangeFailRate(cfr), deploys,
                classifyDeploymentFrequency(deploys), leadTime, classifyLeadTime(leadTime.p50()),
                mergedPrs, remediationPrs);
    }

    /** Bands apply to the percentage: ELITE ≤ 5, HIGH ≤ 10, MEDIUM ≤ 15, LOW otherwise. */
    public static PerformanceClass classifyChangeFailRate(Double percent) {
        if (percent == null) return null;
        if (percent <= CFR_ELITE_MAX) return PerformanceClass.ELITE;
        if (percent <= CFR_HIGH_MAX) return PerformanceClass.HIGH;
        if (percent <= CFR_MEDIUM_MAX) return PerformanceClass.MEDIUM;
        return PerformanceClass.LOW;
    }

    public static PerformanceClass classifyDeploymentFrequency(Double perWeek) {
        if (perWeek == null) return null;
        if (perWeek > DEPLOYS_ELITE_MIN) return PerformanceClass.ELITE;
        if (perWeek > DEPLOYS_HIGH_MIN) return PerformanceClass.HIGH;
        if (perWeek > DEPLOYS_MEDIUM_MIN) return PerformanceClass.MEDIUM;
        return PerformanceClass.LOW;
    }

    public static PerformanceClass classifyLeadTime(Double hours) {
        if (hours == null) return null;
        if (hours < LEAD_ELITE_MAX_HOURS) return PerformanceClass.ELITE;
        if (hours < LEAD_HIGH_MAX_HOURS) return PerformanceClass.HIGH;
        if (hours < LEAD_MEDIUM_MAX_HOURS) return PerformanceClass.MEDIUM;
        return PerformanceClass.LOW;
    }

    /** Weeks of a window, never below one: {@code max(1, days / 7)}. */
    public static double weeks(Instant from, Instant to) {
        double days = Duration.between(from, to).getSeconds() / SECONDS_PER_DAY;
        return Math.max(MIN_WEEKS, days / DAYS_PER_WEEK);
    }

    private static boolean inWindow(Instant at, Instant from, Instant to) {
        return at != null && !at.isBefore(from) && at.isBefore(to);
    }
}
