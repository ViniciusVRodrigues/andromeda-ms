package andromeda.sei.ms.feature.auth.application.usecase;

import andromeda.sei.ms.feature.auth.application.dto.CompleteAccountInput;
import andromeda.sei.ms.feature.auth.domain.exception.InvalidInviteTokenException;
import andromeda.sei.ms.feature.azure.application.usecase.AutoLinkAzureIdentitiesUseCase;
import andromeda.sei.ms.feature.organization.domain.Organization;
import andromeda.sei.ms.feature.organization.domain.OrganizationRepository;
import andromeda.sei.ms.feature.role.domain.Role;
import andromeda.sei.ms.feature.role.domain.RoleRepository;
import andromeda.sei.ms.feature.usage.application.usecase.LinkFunnelUseCase;
import andromeda.sei.ms.feature.usage.application.usecase.RecordLoginUseCase;
import andromeda.sei.ms.feature.user.application.port.PasswordHasher;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** INV1: an invite of a deleted organization is refused, like an unknown token. */
@ExtendWith(MockitoExtension.class)
class CompleteAccountUseCaseTest {

    private static final Instant NOW = Instant.now();

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private PasswordHasher passwordHasher;
    @Mock private RecordLoginUseCase recordLoginUseCase;
    @Mock private LinkFunnelUseCase linkFunnelUseCase;
    @Mock private AutoLinkAzureIdentitiesUseCase autoLinkAzureIdentitiesUseCase;
    @Mock private OrganizationRepository organizationRepository;

    private CompleteAccountUseCase useCase() {
        return new CompleteAccountUseCase(userRepository, roleRepository, passwordHasher, recordLoginUseCase,
                linkFunnelUseCase, autoLinkAzureIdentitiesUseCase, organizationRepository);
    }

    private User invited() {
        return new User("user-1", "org-A", "ana@acme.com", null, null, "role-1", "tok", null, null, null,
                null, true, false, 0L, NOW, NOW);
    }

    private static final CompleteAccountInput INPUT = new CompleteAccountInput("tok", "Ana", "secret-pw-123", null);

    @Test
    void should_refuseInvite_when_organizationIsDeleted() {
        when(userRepository.findByInviteToken("tok")).thenReturn(Optional.of(invited()));
        when(organizationRepository.findById("org-A")).thenReturn(Optional.empty()); // tombstone / inactive

        assertThatThrownBy(() -> useCase().execute(INPUT)).isInstanceOf(InvalidInviteTokenException.class);

        verify(userRepository, never()).save(any());
        verify(recordLoginUseCase, never()).execute(any(), any(), any());
    }

    @Test
    void should_completeInvite_when_organizationIsActive() {
        when(userRepository.findByInviteToken("tok")).thenReturn(Optional.of(invited()));
        when(organizationRepository.findById("org-A")).thenReturn(Optional.of(
                new Organization("org-A", "Acme", "acme", null, null, null, null, null, true, 1L, NOW, NOW)));
        when(passwordHasher.hash("secret-pw-123")).thenReturn("hash");
        when(roleRepository.findById("role-1")).thenReturn(Optional.of(
                new Role("role-1", "org-A", "DEV", "DEV", false, null, List.of("DASHBOARD_INDIVIDUAL"), true, 0L, NOW, NOW)));

        assertThat(useCase().execute(INPUT).orgId()).isEqualTo("org-A");
        verify(userRepository).save(any());
    }
}
