package andromeda.sei.ms.feature.agent.domain.exception;

import andromeda.sei.ms.shared.domain.exception.DomainException;

/**
 * Error of the token endpoint in the RFC 6749/8628 vocabulary ({@code authorization_pending},
 * {@code slow_down}, {@code access_denied}, {@code expired_token}, {@code invalid_grant}...). The
 * code IS the RFC {@code error}; the controller answers 400 {@code {error, error_description}}.
 */
public class AgentOAuthException extends DomainException {

    public AgentOAuthException(String error, String description) {
        super(error, description);
    }

    public String getError() {
        return getCode();
    }
}
