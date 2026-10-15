package andromeda.sei.ms.feature.usage.domain;

import andromeda.sei.ms.shared.domain.exception.BusinessRuleException;

/** Domain-level backstop: the controller already answers 400 for these. The reason never carries client data. */
public class FunnelEventInvalidException extends BusinessRuleException {

    public FunnelEventInvalidException(String reason) {
        super("FUNNEL_EVENT_INVALID", "Evento de funil inválido: " + reason);
    }
}
