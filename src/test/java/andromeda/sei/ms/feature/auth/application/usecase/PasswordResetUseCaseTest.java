package andromeda.sei.ms.feature.auth.application.usecase;

import andromeda.sei.ms.feature.auth.domain.PasswordReset;
import andromeda.sei.ms.feature.auth.domain.PasswordResetRepository;
import andromeda.sei.ms.feature.auth.domain.PasswordResetTokens;
import andromeda.sei.ms.feature.auth.domain.exception.InvalidPasswordResetTokenException;
import andromeda.sei.ms.feature.auth.domain.exception.WeakPasswordException;
import andromeda.sei.ms.feature.user.application.port.PasswordHasher;
import andromeda.sei.ms.feature.user.domain.User;
import andromeda.sei.ms.feature.user.domain.UserRepository;
import andromeda.sei.ms.shared.application.port.EmailService;
import andromeda.sei.ms.shared.application.port.FrontendUrlResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordResetUseCaseTest {

    @Mock PasswordResetRepository resets;
    @Mock UserRepository users;
    @Mock EmailService emailService;
    @Mock FrontendUrlResolver frontendUrlResolver;
    @Mock PasswordHasher passwordHasher;

    @InjectMocks RequestPasswordResetUseCase request;

    private ConfirmPasswordResetUseCase confirm() {
        return new ConfirmPasswordResetUseCase(resets, users, passwordHasher);
    }

    private User user(String email) {
        return new User(null, "org-1", email, "hash-antigo", "Dev", "role-1", null,
                null, null, null, null, true, true, null, Instant.now(), Instant.now());
    }

    // ── Emissão ──────────────────────────────────────────────────────────────

    @Test
    void unknownAddressIsIndistinguishableAndSendsNothing() {
        when(users.existsByEmail("ninguem@example.test")).thenReturn(false);

        assertThatNoException().isThrownBy(() -> request.execute("ninguem@example.test"));

        verifyNoInteractions(emailService);
        verify(resets, never()).save(any());
    }

    @Test
    void issuingANewLinkInvalidatesThePreviousOne() {
        Instant now = Instant.now();
        PasswordReset previous = new PasswordReset("id-1", "dev@example.test", "hash-antigo",
                now.plus(Duration.ofMinutes(30)), null, now);
        when(users.existsByEmail("dev@example.test")).thenReturn(true);
        when(resets.findAllByEmailAndUsedAtIsNull("dev@example.test")).thenReturn(List.of(previous));
        when(frontendUrlResolver.resolve()).thenReturn("https://app.test");

        request.execute("dev@example.test");

        assertThat(previous.getUsedAt()).as("o link anterior deixa de valer").isNotNull();
        /* O envio saiu da requisicao para nao vazar a existencia da conta pela
         * latencia, entao a verificacao precisa aguardar a outra thread. */
        verify(emailService, timeout(2000))
                .sendPasswordReset(eq("dev@example.test"), contains("/redefinir-senha?token="));
    }

    @Test
    void deliveryFailureDoesNotChangeTheOutcome() {
        when(users.existsByEmail("dev@example.test")).thenReturn(true);
        when(resets.findAllByEmailAndUsedAtIsNull(anyString())).thenReturn(List.of());
        when(frontendUrlResolver.resolve()).thenReturn("https://app.test");
        doThrow(new RuntimeException("brevo fora")).when(emailService).sendPasswordReset(anyString(), anyString());

        assertThatNoException()
                .as("falhar o envio nao pode virar o sinal que estamos evitando dar")
                .isThrownBy(() -> request.execute("dev@example.test"));

        verify(emailService, timeout(2000)).sendPasswordReset(anyString(), anyString());
    }

    @Test
    void theRawTokenIsNeverPersisted() {
        when(users.existsByEmail("dev@example.test")).thenReturn(true);
        when(resets.findAllByEmailAndUsedAtIsNull(anyString())).thenReturn(List.of());
        when(frontendUrlResolver.resolve()).thenReturn("https://app.test");

        request.execute("dev@example.test");

        ArgumentCaptor<PasswordReset> saved = ArgumentCaptor.forClass(PasswordReset.class);
        verify(resets).save(saved.capture());
        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(emailService, timeout(2000)).sendPasswordReset(anyString(), link.capture());

        String token = link.getValue().substring(link.getValue().indexOf("token=") + 6);
        assertThat(saved.getValue().getTokenHash()).isNotEqualTo(token);
        assertThat(saved.getValue().getTokenHash()).isEqualTo(PasswordResetTokens.hash(token));
    }

    // ── Consumo ──────────────────────────────────────────────────────────────

    @Test
    void expiredLinkIsRefusedAndPasswordUnchanged() {
        Instant past = Instant.now().minus(Duration.ofHours(1));
        when(resets.findByTokenHash(anyString())).thenReturn(Optional.of(
                new PasswordReset("id", "dev@example.test", "hash", past, null, past)));

        assertThatThrownBy(() -> confirm().execute("token", "Senha!1"))
                .isInstanceOf(InvalidPasswordResetTokenException.class);
        verify(users, never()).save(any());
    }

    @Test
    void alreadyUsedLinkIsRefused() {
        Instant now = Instant.now();
        when(resets.findByTokenHash(anyString())).thenReturn(Optional.of(
                new PasswordReset("id", "dev@example.test", "hash",
                        now.plus(Duration.ofMinutes(30)), now, now)));

        assertThatThrownBy(() -> confirm().execute("token", "Senha!1"))
                .isInstanceOf(InvalidPasswordResetTokenException.class);
        verify(users, never()).save(any());
    }

    @Test
    void unknownTokenIsRefusedTheSameWay() {
        when(resets.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> confirm().execute("qualquer", "Senha!1"))
                .isInstanceOf(InvalidPasswordResetTokenException.class);
    }

    @Test
    void weakPasswordIsRejectedOnTheServerAndKeepsTheLinkUsable() {
        Instant now = Instant.now();
        PasswordReset valid = new PasswordReset("id", "dev@example.test", "hash",
                now.plus(Duration.ofMinutes(30)), null, now);
        when(resets.findByTokenHash(anyString())).thenReturn(Optional.of(valid));

        assertThatThrownBy(() -> confirm().execute("token", "fraca"))
                .isInstanceOf(WeakPasswordException.class);

        assertThat(valid.getUsedAt()).as("senha fraca nao queima o link").isNull();
        verify(users, never()).save(any());
    }

    @Test
    void validLinkReplacesThePasswordAndBurnsTheToken() {
        Instant now = Instant.now();
        PasswordReset valid = new PasswordReset("id", "dev@example.test", "hash",
                now.plus(Duration.ofMinutes(30)), null, now);
        User target = user("dev@example.test");
        when(resets.findByTokenHash(anyString())).thenReturn(Optional.of(valid));
        when(users.findByEmail("dev@example.test")).thenReturn(Optional.of(target));
        when(passwordHasher.hash("Nova!123")).thenReturn("hash-novo");

        confirm().execute("token", "Nova!123");

        assertThat(valid.getUsedAt()).isNotNull();
        assertThat(target.getPasswordHash()).isEqualTo("hash-novo");
        verify(users).save(target);
    }
}
