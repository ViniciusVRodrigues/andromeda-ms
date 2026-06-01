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

    @InjectMocks
    private UpdateRolePermissionsUseCase updateRolePermissionsUseCase;

    @InjectMocks
    private DeleteRoleUseCase deleteRoleUseCase;

    private static final String ORG_ID = "org-1";
    private static final String OTHER_ORG_ID = "org-99";
    private static final Instant NOW = Instant.now();

    private Role customRole(String id, List<String> codes) {
        return new Role(id, ORG_ID, "Custom Role", false, null, new ArrayList<>(codes), true, 0L, NOW, NOW);
    }

    private Role defaultRole(String id) {
        return new Role(id, null, "ADMIN", true, null, new ArrayList<>(List.of("USER_MANAGE")), true, 0L, NOW, NOW);
    }

    private Role otherOrgRole(String id) {
        return new Role(id, OTHER_ORG_ID, "Foreign Role", false, null, new ArrayList<>(), true, 0L, NOW, NOW);
    }

    // ── UpdateRolePermissionsUseCase ─────────────────────────────────────────

    @Test
    void updatePermissions_shouldAddAndRemovePermissions() {
        var role = customRole("role-1", List.of("AZURE_READ", "GITHUB_READ"));
        var updated = customRole("role-1", List.of("AZURE_READ", "AI_READ_OWN"));

        when(roleRepository.findById("role-1")).thenReturn(Optional.of(role));
        when(roleRepository.save(any(Role.class))).thenReturn(updated);

        RoleOutput output = updateRolePermissionsUseCase.execute(
                new UpdateRolePermissionsInput("role-1", List.of("AI_READ_OWN"), List.of("GITHUB_READ")),
                ORG_ID
        );

        assertThat(output.permissionCodes()).containsExactlyInAnyOrder("AZURE_READ", "AI_READ_OWN");
        verify(roleRepository).save(any(Role.class));
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
