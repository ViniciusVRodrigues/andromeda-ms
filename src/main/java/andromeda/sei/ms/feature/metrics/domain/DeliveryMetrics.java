package andromeda.sei.ms.feature.metrics.domain;

import andromeda.sei.ms.shared.domain.metrics.DurationStat;

/**
 * Delivery (DORA-style) metrics of a scope and window, as explicit proxies (delivery-metrics).
 *
 * <p>{@code changeFailRatePercent} is always a percentage in {@code [0, 100]}. With
 * {@link DeliveryMethod#UNAVAILABLE} every value and class is {@code null}; {@code mergedPrs} and
 * {@code remediationPrs} count merged pull requests into the default branch and are 0 without
 * repository data.
 */
public record DeliveryMetrics(
        DeliveryMethod method,
        Double changeFailRatePercent,
        PerformanceClass changeFailRateClass,
        Double deploymentsPerWeek,
        PerformanceClass deploymentFrequencyClass,
        DurationStat changeLeadTime,
        PerformanceClass changeLeadTimeClass,
        int mergedPrs,
        int remediationPrs) {

    public static DeliveryMetrics unavailable() {
        return new DeliveryMetrics(DeliveryMethod.UNAVAILABLE, null, null, null, null,
                DurationStat.empty(), null, 0, 0);
    }
}
