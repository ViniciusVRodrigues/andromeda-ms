package andromeda.sei.ms.feature.organization.presentation.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * @param remediationBranchPrefixes may be empty (no prefix rule); a blank entry answers 422
 *                                  {@code METRIC_SETTINGS_INVALID} (validated in the use case)
 */
public record MetricSettingsRequest(@NotNull List<String> remediationBranchPrefixes,
                                    @NotNull Boolean bugLinkedCountsAsRemediation,
                                    @NotNull Boolean revertCountsAsRemediation) {}
