package andromeda.sei.ms.feature.organization.domain.exception;

import andromeda.sei.ms.shared.domain.exception.BusinessRuleException;

public class MetricSettingsInvalidException extends BusinessRuleException {

    public MetricSettingsInvalidException(String reason) {
        super("METRIC_SETTINGS_INVALID", "Invalid metric settings: " + reason);
    }
}
