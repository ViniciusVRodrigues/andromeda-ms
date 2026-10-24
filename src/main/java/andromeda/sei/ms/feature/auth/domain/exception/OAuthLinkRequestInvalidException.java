package andromeda.sei.ms.feature.auth.domain.exception;

import andromeda.sei.ms.shared.domain.exception.DomainException;

/** Pedido de ticket invalido (proposito ou URL do Azure): HTTP 400 no GlobalExceptionHandler. */
public class OAuthLinkRequestInvalidException extends DomainException {

    public OAuthLinkRequestInvalidException(String code, String message) {
        super(code, message);
    }
}
