package andromeda.sei.ms.feature.organization.application.dto;

import java.util.List;

public record UpdateMetricSettingsInput(List<String> remediationBranchPrefixes,
                                        boolean bugLinkedCountsAsRemediation,
                                        boolean revertCountsAsRemediation) {}
