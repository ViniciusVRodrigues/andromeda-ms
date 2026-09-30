package andromeda.sei.ms.feature.organization.presentation.dto;

import andromeda.sei.ms.feature.organization.application.dto.MetricSettingsOutput;

import java.util.List;

public record MetricSettingsResponse(List<String> remediationBranchPrefixes,
                                     boolean bugLinkedCountsAsRemediation,
                                     boolean revertCountsAsRemediation) {

    public static MetricSettingsResponse from(MetricSettingsOutput output) {
        return new MetricSettingsResponse(output.remediationBranchPrefixes(),
                output.bugLinkedCountsAsRemediation(), output.revertCountsAsRemediation());
    }
}
