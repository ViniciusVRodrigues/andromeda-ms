package andromeda.sei.ms.feature.auth.domain.exception;

import andromeda.sei.ms.shared.domain.exception.BusinessRuleException;

public class OtpInvalidOrExpiredException extends BusinessRuleException {
    public OtpInvalidOrExpiredException() {
        super("OTP_INVALID_OR_EXPIRED", "OTP code is invalid, already used, or has expired");
    }
}
