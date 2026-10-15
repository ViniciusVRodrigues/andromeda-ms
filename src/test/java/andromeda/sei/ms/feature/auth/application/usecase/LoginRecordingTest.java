package andromeda.sei.ms.feature.auth.application.usecase;

import andromeda.sei.ms.feature.auth.application.dto.CompleteAccountInput;
import andromeda.sei.ms.feature.auth.application.dto.LoginInput;
import andromeda.sei.ms.feature.auth.domain.exception.InvalidCredentialsException;
import andromeda.sei.ms.feature.role.domain.Role;
import andromeda.sei.ms.feature.role.domain.RoleRepository;
import andromeda.sei.ms.feature.usage.application.usecase.RecordLoginUseCase;
import andromeda.sei.ms.feature.usage.domain.LoginMethod;
import andromeda.sei.ms.feature.user.application.port.PasswordHasher;
import andromeda.sei.ms.feature.user.application.port.PasswordVerifier;
import andromeda.sei.ms.feature.user.domain.User;
import andromeda.sei.ms.feature.user.domain.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Login, invite acceptance and signup record the login on the server and never fail because of it. */
@ExtendWith(MockitoExtension.class)
class LoginRecordingTest {

    private static final Instant NOW = Instant.now();

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private PasswordVerifier passwordVerifier;
    @Mock private PasswordHasher passwordHasher;
    @Mock private RecordLoginUseCase recordLoginUseCase;

    private User user() {
        return new User("user-1", "org-A", "ana@acme.com", "hash", "Ana", "role-1", null, null, null, null,
                null, true, true, 0L, NOW, NOW);
    }

    private Role role() {
        return new Role("role-1", "org-A", "DEV", "DEV", false, null, List.of("DASHBOARD_INDIVIDUAL"), true, 0L, NOW, NOW);
    }

    private LoginUseCase login() {
        return new LoginUseCase(userRepository, roleRepository, passwordVerifier, recordLoginUseCase);
    }

    @Test
    void login_shouldRecordWithPasswordMethod_onSuccess() {
        when(userRepository.findByEmail("ana@acme.com")).thenReturn(Optional.of(user()));
        when(passwordVerifier.verify("pw", "hash")).thenReturn(true);
        when(roleRepository.findById("role-1")).thenReturn(Optional.of(role()));

        var out = login().execute(new LoginInput("ana@acme.com", "pw"));

        assertThat(out.userId()).isEqualTo("user-1");
        verify(recordLoginUseCase).execute("user-1", "org-A", LoginMethod.PASSWORD);
    }

    @Test
    void login_shouldNotRecord_onWrongPassword() {
        when(userRepository.findByEmail("ana@acme.com")).thenReturn(Optional.of(user()));
        when(passwordVerifier.verify("bad", "hash")).thenReturn(false);

        assertThatThrownBy(() -> login().execute(new LoginInput("ana@acme.com", "bad")))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(recordLoginUseCase, never()).execute(any(), any(), any());
    }

    @Test
    void login_shouldReturnTheToken_evenWhenTheRecorderThrows() {
        when(userRepository.findByEmail("ana@acme.com")).thenReturn(Optional.of(user()));
        when(passwordVerifier.verify("pw", "hash")).thenReturn(true);
        when(roleRepository.findById("role-1")).thenReturn(Optional.of(role()));
        doThrow(new IllegalStateException("boom")).when(recordLoginUseCase).execute(any(), any(), any());

        var out = login().execute(new LoginInput("ana@acme.com", "pw"));

        assertThat(out.userId()).isEqualTo("user-1");
        assertThat(out.permissionCodes()).containsExactly("DASHBOARD_INDIVIDUAL");
    }

    @Test
    void invite_shouldRecordWithInviteMethod_onSuccess() {
        User invited = user();
        invited.setConfirmed(false);
        when(userRepository.findByInviteToken("tok")).thenReturn(Optional.of(invited));
        when(passwordHasher.hash("pw")).thenReturn("hash");
        when(roleRepository.findById("role-1")).thenReturn(Optional.of(role()));

        new CompleteAccountUseCase(userRepository, roleRepository, passwordHasher, recordLoginUseCase)
                .execute(new CompleteAccountInput("tok", "Ana", "pw"));

        verify(recordLoginUseCase).execute("user-1", "org-A", LoginMethod.INVITE);
    }

    @Test
    void invite_shouldReturnTheToken_evenWhenTheRecorderThrows() {
        User invited = user();
        when(userRepository.findByInviteToken("tok")).thenReturn(Optional.of(invited));
        when(passwordHasher.hash("pw")).thenReturn("hash");
        when(roleRepository.findById("role-1")).thenReturn(Optional.of(role()));
        doThrow(new IllegalStateException("boom")).when(recordLoginUseCase).execute(any(), any(), any());

        var out = new CompleteAccountUseCase(userRepository, roleRepository, passwordHasher, recordLoginUseCase)
                .execute(new CompleteAccountInput("tok", "Ana", "pw"));

        assertThat(out.userId()).isEqualTo("user-1");
    }
}
