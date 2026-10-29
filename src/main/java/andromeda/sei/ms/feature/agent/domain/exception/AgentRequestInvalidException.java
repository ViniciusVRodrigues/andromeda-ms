package andromeda.sei.ms.feature.agent.domain.exception;

import andromeda.sei.ms.shared.domain.exception.DomainException;

/** A query parameter of an agent read endpoint is invalid, such as a period over 31 days (HTTP 400). */
public class AgentRequestInvalidException extends DomainException {

    public AgentRequestInvalidException(String detail) {
        super("AGENT_REQUEST_INVALID", "Pedido inválido: " + detail);
    }
}
