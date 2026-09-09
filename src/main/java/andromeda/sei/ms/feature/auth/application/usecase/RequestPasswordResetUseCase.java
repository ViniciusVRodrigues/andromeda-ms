package andromeda.sei.ms.feature.auth.application.usecase;

import andromeda.sei.ms.feature.auth.domain.PasswordReset;
import andromeda.sei.ms.feature.auth.domain.PasswordResetRepository;
import andromeda.sei.ms.feature.auth.domain.PasswordResetTokens;
import andromeda.sei.ms.feature.user.domain.UserRepository;
import andromeda.sei.ms.shared.application.port.EmailService;
import andromeda.sei.ms.shared.application.port.FrontendUrlResolver;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;
import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.time.Instant;

/**
 * Emite um link de redefinicao de senha.
 *
 * Nao existe caminho de erro para e-mail desconhecido: quem chama recebe a
 * mesma resposta em todos os casos. Sinalizar "nao encontrado" transformaria
 * o endpoint publico num verificador de contas.
 */
@Component
@RequiredArgsConstructor
public class RequestPasswordResetUseCase {

    private static final Logger log = LoggerFactory.getLogger(RequestPasswordResetUseCase.class);
    private static final Duration TTL = Duration.ofMinutes(30);

    /* Pool minimo e dedicado: o envio nao deve competir com as threads que
     * atendem requisicoes nem atrasar a resposta. */
    private static final Executor MAIL_EXECUTOR =
            Executors.newFixedThreadPool(2, r -> {
                Thread t = new Thread(r, "password-reset-mail");
                t.setDaemon(true);
                return t;
            });

    private final PasswordResetRepository passwordResetRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final FrontendUrlResolver frontendUrlResolver;

    public void execute(String rawEmail) {
        String email = rawEmail == null ? "" : rawEmail.trim().toLowerCase();
        if (email.isEmpty()) return;

        /* Conta inexistente termina aqui, em silencio — sem excecao e sem
         * diferenca observavel para quem chamou. */
        if (!userRepository.existsByEmail(email)) {
            log.debug("password reset requested for unknown address — no mail sent");
            return;
        }

        /* Emitir um novo invalida os anteriores, como no fluxo de verificacao
         * de e-mail: so o link mais recente vale. */
        Instant now = Instant.now();
        passwordResetRepository.findAllByEmailAndUsedAtIsNull(email).forEach(previous -> {
            previous.markUsed(now);
            passwordResetRepository.save(previous);
        });

        String token = PasswordResetTokens.generate();
        passwordResetRepository.save(new PasswordReset(
                null, email, PasswordResetTokens.hash(token), now.plus(TTL), null, now));

        String link = frontendUrlResolver.resolve()
                + "/redefinir-senha?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);

        /* Envio FORA da requisicao. Sincrono, a chamada ao provedor levava ~1s
         * para e-mail existente contra ~6ms para inexistente — a latencia
         * denunciava a existencia da conta, que e justamente o que a resposta
         * 202 uniforme tenta esconder. A falha tambem nao pode alterar a
         * resposta, entao fica no log. */
        MAIL_EXECUTOR.execute(() -> {
            try {
                emailService.sendPasswordReset(email, link);
            } catch (RuntimeException ex) {
                log.error("failed to deliver password reset e-mail", ex);
            }
        });
    }
}
