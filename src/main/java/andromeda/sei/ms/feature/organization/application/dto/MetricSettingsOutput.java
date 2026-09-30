package andromeda.sei.ms.feature.organization.application.dto;

import andromeda.sei.ms.feature.organization.domain.MetricSettings;
import andromeda.sei.ms.feature.organization.domain.RemediationRules;

import java.util.List;

public record MetricSettingsOutput(List<String> remediationBranchPrefixes,
                                   boolean bugLinkedCountsAsRemediation,
                                   boolean revertCountsAsRemediation) {

    public static MetricSettingsOutput from(MetricSettings settings) {
        RemediationRules rules = settings.remediationRules();
        return new MetricSettingsOutput(rules.branchPrefixes(), rules.bugLinkedCountsAsRemediation(),
                rules.revertCountsAsRemediation());
    }
}
