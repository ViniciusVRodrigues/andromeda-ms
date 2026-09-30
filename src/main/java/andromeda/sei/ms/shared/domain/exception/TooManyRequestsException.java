package andromeda.sei.ms.shared.domain.exception;

/**
 * Pedido recusado por limite de taxa ou de capacidade (HTTP 429). Carrega em quantos segundos
 * vale a pena tentar de novo, que o handler global devolve no header {@code Retry-After}.
 */
public abstract class TooManyRequestsException extends DomainException {

    private final long retryAfterSeconds;

    protected TooManyRequestsException(String code, String message, long retryAfterSeconds) {
        super(code, message);
        this.retryAfterSeconds = Math.max(retryAfterSeconds, 1);
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
