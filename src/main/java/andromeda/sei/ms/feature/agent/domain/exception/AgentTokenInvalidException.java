package andromeda.sei.ms.feature.agent.domain.exception;

import andromeda.sei.ms.shared.domain.exception.UnauthorizedException;

/** Missing, unknown, expired or revoked agent access token (HTTP 401). */
public class AgentTokenInvalidException extends UnauthorizedException {

    public AgentTokenInvalidException() {
        super("AGENT_TOKEN_INVALID", "Token do agente inválido ou expirado");
    }
}
