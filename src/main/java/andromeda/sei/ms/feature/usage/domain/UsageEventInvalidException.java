package andromeda.sei.ms.feature.usage.domain;

import andromeda.sei.ms.shared.domain.exception.BusinessRuleException;

/** A usage event whose fields do not fit its type (HTTP 422). The reason never carries client data. */
public class UsageEventInvalidException extends BusinessRuleException {

    public UsageEventInvalidException(String reason) {
        super("USAGE_EVENT_INVALID", "Evento de uso inválido: " + reason);
    }
}
