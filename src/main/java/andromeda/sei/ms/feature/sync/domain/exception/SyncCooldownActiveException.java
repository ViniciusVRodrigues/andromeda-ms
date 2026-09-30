package andromeda.sei.ms.feature.sync.domain.exception;

import andromeda.sei.ms.shared.domain.exception.TooManyRequestsException;

/** Todos os bindings do pedido estao dentro do cooldown de 30s (HTTP 429 + {@code Retry-After}). */
public class SyncCooldownActiveException extends TooManyRequestsException {

    public SyncCooldownActiveException(long retryAfterSeconds) {
        super("SYNC_COOLDOWN_ACTIVE",
                "Every binding was synced moments ago; retry in " + retryAfterSeconds + "s",
                retryAfterSeconds);
    }
}
