package andromeda.sei.ms.feature.organization.infrastructure.persistence;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Subdocumento {@code organizations.metricSettings.remediationRules}; campos nulos leem os defaults. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RemediationRulesDocument {

    private List<String> branchPrefixes;
    private Boolean bugLinkedCountsAsRemediation;
    private Boolean revertCountsAsRemediation;
}
