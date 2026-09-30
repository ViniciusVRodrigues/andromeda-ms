package andromeda.sei.ms.feature.organization.domain;

import java.util.Objects;

/**
 * Per-organization knobs of metric definitions (metrics-correctness). Today only the remediation
 * rules of the Change Fail Rate proxy; the review SLA stays a constant until there is demand.
 */
public record MetricSettings(RemediationRules remediationRules) {

    public MetricSettings {
        Objects.requireNonNull(remediationRules, "remediationRules");
    }

    public static MetricSettings defaults() {
        return new MetricSettings(RemediationRules.defaults());
    }
}
