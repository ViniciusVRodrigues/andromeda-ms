package andromeda.sei.ms.feature.organization.infrastructure.persistence;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Subdocumento embutido em {@code organizations.metricSettings}. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MetricSettingsDocument {

    private RemediationRulesDocument remediationRules;
}
