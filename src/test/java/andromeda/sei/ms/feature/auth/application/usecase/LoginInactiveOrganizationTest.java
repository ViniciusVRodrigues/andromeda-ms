package andromeda.sei.ms.feature.auth.application.usecase;

import andromeda.sei.ms.feature.auth.application.dto.LoginInput;
import andromeda.sei.ms.feature.auth.domain.exception.InvalidCredentialsException;
import andromeda.sei.ms.feature.organization.domain.Organization;
import andromeda.sei.ms.feature.organization.domain.OrganizationRepository;
import andromeda.sei.ms.feature.role.domain.Role;
import andromeda.sei.ms.feature.role.domain.RoleRepository;
import andromeda.sei.ms.feature.usage.application.usecase.RecordLoginUseCase;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** A user of a deleted (inactive) organization cannot log in, with the same error as a wrong password. */
@ExtendWith(MockitoExtension.class)
class LoginInactiveOrganizationTest {

    private static final Instant NOW = Instant.now();

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private PasswordVerifier passwordVerifier;
    @Mock private RecordLoginUseCase recordLoginUseCase;
    @Mock private OrganizationRepository organizationRepository;

    private LoginUseCase login() {
        return new LoginUseCase(userRepository, roleRepository, passwordVerifier, recordLoginUseCase,
                organizationRepository);
    }

    private void givenValidCredentials() {
        User user = new User("user-1", "org-A", "ana@acme.com", "hash", "Ana", "role-1", null, null, null, null,
                null, true, true, 0L, NOW, NOW);
        when(userRepository.findByEmail("ana@acme.com")).thenReturn(Optional.of(user));
        lenient().when(passwordVerifier.verify("pw", "hash")).thenReturn(true);
        lenient().when(roleRepository.findById("role-1")).thenReturn(Optional.of(
                new Role("role-1", "org-A", "DEV", "DEV", false, null, List.of("DASHBOARD_INDIVIDUAL"), true, 0L, NOW, NOW)));
    }

    private static Organization organization(boolean active) {
        return new Organization("org-A", "Acme", "acme", null, null, null, null, null, active, 1L, NOW, NOW);
    }

    @Test
    void should_throwInvalidCredentials_when_organizationIsInactive() {
        givenValidCredentials();
        when(organizationRepository.findById("org-A")).thenReturn(Optional.empty()); // findById filters inactive

        assertThatThrownBy(() -> login().execute(new LoginInput("ana@acme.com", "pw")))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(recordLoginUseCase, never()).execute(any(), any(), any());
    }

    @Test
    void should_login_when_organizationIsActive() {
        givenValidCredentials();
        when(organizationRepository.findById("org-A")).thenReturn(Optional.of(organization(true)));

        assertThat(login().execute(new LoginInput("ana@acme.com", "pw")).orgId()).isEqualTo("org-A");
    }
}
