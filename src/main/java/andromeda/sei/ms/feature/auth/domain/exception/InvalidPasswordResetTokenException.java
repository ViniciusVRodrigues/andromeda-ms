package andromeda.sei.ms.feature.auth.domain.exception;

import andromeda.sei.ms.shared.domain.exception.BusinessRuleException;

/**
 * Token desconhecido, expirado ou ja usado — os tres casos compartilham a
 * mesma mensagem de proposito: distingui-los diria a quem tenta adivinhar
 * qual parte da tentativa acertou.
 */
public class InvalidPasswordResetTokenException extends BusinessRuleException {

    public InvalidPasswordResetTokenException() {
        super("PASSWORD_RESET_TOKEN_INVALID",
                "Este link de redefinição não é mais válido. Solicite um novo.");
    }
}
