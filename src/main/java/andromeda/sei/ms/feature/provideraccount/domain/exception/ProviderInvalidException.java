package andromeda.sei.ms.feature.provideraccount.domain.exception;

import andromeda.sei.ms.shared.domain.exception.DomainException;

/**
 * An unknown provider path value — a client input error, mapped to HTTP 400 by a dedicated
 * handler in {@code GlobalExceptionHandler} (does not extend the generic subtypes since none
 * of them map to 400).
 */
public class ProviderInvalidException extends DomainException {

    public ProviderInvalidException(String value) {
        super("PROVIDER_INVALID", "Unknown provider: " + value);
    }
}
