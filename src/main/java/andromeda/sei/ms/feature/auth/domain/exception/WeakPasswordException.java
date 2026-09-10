package andromeda.sei.ms.feature.auth.domain.exception;

import andromeda.sei.ms.shared.domain.exception.BusinessRuleException;

public class WeakPasswordException extends BusinessRuleException {

    public WeakPasswordException() {
        super("PASSWORD_TOO_WEAK",
                "A senha precisa ter ao menos 6 caracteres, uma letra maiúscula e um caractere especial.");
    }
}
