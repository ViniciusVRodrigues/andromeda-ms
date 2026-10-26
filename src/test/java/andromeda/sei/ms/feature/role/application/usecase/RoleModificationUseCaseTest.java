package andromeda.sei.ms.feature.role.application.usecase;

import andromeda.sei.ms.feature.role.application.dto.RoleOutput;
import andromeda.sei.ms.feature.role.application.dto.UpdateRolePermissionsInput;
import andromeda.sei.ms.feature.role.domain.Role;
import andromeda.sei.ms.feature.role.domain.RoleRepository;
import andromeda.sei.ms.feature.role.domain.exception.DefaultRoleModificationException;
import andromeda.sei.ms.feature.role.domain.exception.RoleInUseException;
import andromeda.sei.ms.feature.role.domain.exception.RoleNotFoundException;
import andromeda.sei.ms.feature.user.domain.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleModificationUseCaseTest {

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private org.springframework.context.ApplicationEventPublisher eventPublisher;


    @InjectMocks
    private UpdateRolePermissionsUseCase updateRolePermissionsUseCase;

    @InjectMocks
    private DeleteRoleUseCase deleteRoleUseCase;

    private static final String ORG_ID = "org-1";
    private static final String OTHER_ORG_ID = "org-99";
    private static final Instant NOW = Instant.now();

    private Role customRole(String id, List<String> codes) {
        return new Role(id, ORG_ID, "Custom Role", "Custom Role", false, null, new ArrayList<>(codes), true, 0L, NOW, NOW);
    }

    private Role defaultRole(String id) {
        return new Role(id, null, "ADMIN", "ADMIN", true, null, new ArrayList<>(List.of("USER_MANAGE")), true, 0L, NOW, NOW);
    }

    private Role otherOrgRole(String id) {
        return new Role(id, OTHER_ORG_ID, "Foreign Role", "Foreign Role", false, null, new ArrayList<>(), true, 0L, NOW, NOW);
    }

    // ── UpdateRolePermissionsUseCase ─────────────────────────────────────────

    @Test
    void updatePermissions_shouldReject422WhenAddingOrganizationDelete() {
        when(roleRepository.findById("role-1")).thenReturn(Optional.of(customRole("role-1", List.of("ROLE_MANAGE"))));

        assertThatThrownBy(() -> updateRolePermissionsUseCase.execute(
                new UpdateRolePermissionsInput("role-1", List.of("ORGANIZATION_DELETE"), List.of()), ORG_ID))
                .isInstanceOf(andromeda.sei.ms.feature.role.domain.exception.RolePermissionNotAssignableException.class);
        org.mockito.Mockito.verify(roleRepository, org.mockito.Mockito.never()).save(any());
        org.mockito.Mockito.verify(eventPublisher, org.mockito.Mockito.never()).publishEvent(any(Object.class));
    }

    @Test
    void updatePermissions_shouldStripInheritedOrganizationDelete() {
        var role = customRole("role-1", List.of("ROLE_MANAGE", "ORGANIZATION_DELETE"));
        when(roleRepository.findById("role-1")).thenReturn(Optional.of(role));
        when(roleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var output = updateRolePermissionsUseCase.execute(
                new UpdateRolePermissionsInput("role-1", List.of("AI_READ_OWN"), List.of()), ORG_ID);

        assertThat(output.permissionCodes()).contains("ROLE_MANAGE", "AI_READ_OWN").doesNotContain("ORGANIZATION_DELETE");
    }

    @Test
    void updatePermissions_shouldAddAndRemovePermissions() {
        var role = customRole("role-1", List.of("AZURE_READ", "REPOS_READ"));
        var updated = customRole("role-1", List.of("AZURE_READ", "AI_READ_OWN"));

        when(roleRepository.findById("role-1")).thenReturn(Optional.of(role));
        when(roleRepository.save(any(Role.class))).thenReturn(updated);

        RoleOutput output = updateRolePermissionsUseCase.execute(
                new UpdateRolePermissionsInput("role-1", List.of("AI_READ_OWN"), List.of("REPOS_READ")),
                ORG_ID
        );

        assertThat(output.permissionCodes()).containsExactlyInAnyOrder("AZURE_READ", "AI_READ_OWN");
        verify(roleRepository).save(any(Role.class));
        verify(eventPublisher).publishEvent(any(andromeda.sei.ms.shared.application.security.RoleChanged.class));
    }

    @Test
    void updatePermissions_shouldStoreCurrentCode_whenOldClientSendsLegacyGithubCode() {
        var role = customRole("role-1", new ArrayList<>(List.of("AZURE_READ")));
        when(roleRepository.findById("role-1")).thenReturn(Optional.of(role));
        when(roleRepository.save(any(Role.class))).thenAnswer(inv -> inv.getArgument(0));

        RoleOutput output = updateRolePermissionsUseCase.execute(
                new UpdateRolePermissionsInput("role-1", List.of("GITHUB_READ", "REPOS_READ"), List.of()),
                ORG_ID
        );

        assertThat(output.permissionCodes()).containsExactly("AZURE_READ", "REPOS_READ");
    }

    /** A role still carrying the GITHUB_READ mirror must not get REPOS_READ back through the alias. */
    @Test
    void should_revokeRenamedCode_when_removingItFromRoleThatStillHoldsTheLegacyMirror() {
        var role = customRole("role-1", List.of("AZURE_READ", "REPOS_READ", "GITHUB_READ"));
        when(roleRepository.findById("role-1")).thenReturn(Optional.of(role));
        when(roleRepository.save(any(Role.class))).thenAnswer(inv -> inv.getArgument(0));

        RoleOutput output = updateRolePermissionsUseCase.execute(
                new UpdateRolePermissionsInput("role-1", List.of(), List.of("REPOS_READ")), ORG_ID);

        assertThat(output.permissionCodes()).containsExactly("AZURE_READ");
    }

    @Test
    void should_revokeCurrentCode_when_oldClientRemovesTheLegacyCode() {
        var role = customRole("role-1", List.of("AZURE_READ", "REPOS_READ"));
        when(roleRepository.findById("role-1")).thenReturn(Optional.of(role));
        when(roleRepository.save(any(Role.class))).thenAnswer(inv -> inv.getArgument(0));

        RoleOutput output = updateRolePermissionsUseCase.execute(
                new UpdateRolePermissionsInput("role-1", List.of(), List.of("GITHUB_READ")), ORG_ID);

        assertThat(output.permissionCodes()).containsExactly("AZURE_READ");
    }

    @Test
    void updatePermissions_shouldThrowForDefaultRole() {
        when(roleRepository.findById("admin")).thenReturn(Optional.of(defaultRole("admin")));

        assertThatThrownBy(() -> updateRolePermissionsUseCase.execute(
                new UpdateRolePermissionsInput("admin", List.of("AI_READ_OWN"), List.of()),
                ORG_ID
        ))
                .isInstanceOf(DefaultRoleModificationException.class);
    }

    @Test
    void updatePermissions_shouldThrowRoleNotFoundForOtherOrgRole() {
        when(roleRepository.findById("foreign")).thenReturn(Optional.of(otherOrgRole("foreign")));

        assertThatThrownBy(() -> updateRolePermissionsUseCase.execute(
                new UpdateRolePermissionsInput("foreign", List.of(), List.of()),
                ORG_ID
        ))
                .isInstanceOf(RoleNotFoundException.class);
    }

    // ── DeleteRoleUseCase ────────────────────────────────────────────────────

    @Test
    void deleteRole_shouldSoftDeleteCustomRole() {
        var role = customRole("role-1", List.of("AZURE_READ"));
        var deleted = customRole("role-1", List.of("AZURE_READ"));
        deleted.setActive(false);

        when(roleRepository.findById("role-1")).thenReturn(Optional.of(role));
        when(userRepository.existsByOrganizationIdAndRoleIdAndActiveTrue(ORG_ID, "role-1")).thenReturn(false);
        when(roleRepository.save(any(Role.class))).thenReturn(deleted);

        deleteRoleUseCase.execute("role-1", ORG_ID);

        verify(roleRepository).save(any(Role.class));
    }

    @Test
    void deleteRole_shouldThrowForDefaultRole() {
        when(roleRepository.findById("admin")).thenReturn(Optional.of(defaultRole("admin")));

        assertThatThrownBy(() -> deleteRoleUseCase.execute("admin", ORG_ID))
                .isInstanceOf(DefaultRoleModificationException.class);
    }

    @Test
    void deleteRole_shouldThrow_whenRoleIsAssignedToActiveUsers() {
        var role = customRole("role-1", List.of("AZURE_READ"));

        when(roleRepository.findById("role-1")).thenReturn(Optional.of(role));
        when(userRepository.existsByOrganizationIdAndRoleIdAndActiveTrue(ORG_ID, "role-1")).thenReturn(true);

        assertThatThrownBy(() -> deleteRoleUseCase.execute("role-1", ORG_ID))
                .isInstanceOf(RoleInUseException.class);
    }
}
