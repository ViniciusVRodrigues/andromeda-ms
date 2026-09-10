package andromeda.sei.ms.feature.auth.application.usecase;

import andromeda.sei.ms.feature.auth.domain.PasswordReset;
import andromeda.sei.ms.feature.auth.domain.PasswordResetRepository;
import andromeda.sei.ms.feature.auth.domain.PasswordResetTokens;
import andromeda.sei.ms.feature.auth.domain.exception.InvalidPasswordResetTokenException;
import andromeda.sei.ms.feature.auth.domain.exception.WeakPasswordException;
import andromeda.sei.ms.feature.user.application.port.PasswordHasher;
import andromeda.sei.ms.feature.user.domain.User;
import andromeda.sei.ms.feature.user.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Consome o link e grava a nova senha.
 *
 * Token desconhecido, expirado e ja usado levantam a MESMA excecao: separar os
 * casos diria a quem tenta adivinhar qual parte da tentativa acertou.
 */
@Component
@RequiredArgsConstructor
public class ConfirmPasswordResetUseCase {

    private static final int MIN_LENGTH = 6;

    private final PasswordResetRepository passwordResetRepository;
    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;

    public void execute(String token, String password) {
        if (token == null || token.isBlank()) {
            throw new InvalidPasswordResetTokenException();
        }

        /* A busca e por hash, entao o segredo nunca entra numa comparacao de
         * string — o lookup ja e a verificacao. */
        PasswordReset reset = passwordResetRepository.findByTokenHash(PasswordResetTokens.hash(token))
                .orElseThrow(InvalidPasswordResetTokenException::new);

        Instant now = Instant.now();
        if (!reset.isUsable(now)) {
            throw new InvalidPasswordResetTokenException();
        }

        /* Validar no servidor: o endpoint e publico e nao pode depender das
         * regras que a tela do signup aplica no cliente. Antes de marcar como
         * usado, para uma senha fraca nao queimar o link. */
        requireStrong(password);

        User user = userRepository.findByEmail(reset.getEmail())
                .orElseThrow(InvalidPasswordResetTokenException::new);

        /* Marca o uso antes de gravar a senha: se a gravacao falhar, o link ja
         * nao vale mais — preferivel a deixa-lo reutilizavel. */
        reset.markUsed(now);
        passwordResetRepository.save(reset);

        user.setPasswordHash(passwordHasher.hash(password));
        user.setConfirmed(true);
        userRepository.save(user);
    }

    /** Mesmas regras exibidas na criacao de conta: 6+, uma maiuscula, um especial. */
    private void requireStrong(String password) {
        if (password == null
                || password.length() < MIN_LENGTH
                || password.chars().noneMatch(Character::isUpperCase)
                || password.chars().noneMatch(ch -> !Character.isLetterOrDigit(ch))) {
            throw new WeakPasswordException();
        }
    }
}
