package andromeda.sei.ms.feature.role.application.usecase;

import andromeda.sei.ms.feature.role.application.dto.CloneRoleInput;
import andromeda.sei.ms.feature.role.application.dto.RoleOutput;
import andromeda.sei.ms.feature.role.domain.Role;
import andromeda.sei.ms.feature.role.domain.RoleRepository;
import andromeda.sei.ms.feature.role.domain.exception.RoleNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleUseCaseTest {

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ListRolesUseCase listRolesUseCase;

    @InjectMocks
    private CloneRoleUseCase cloneRoleUseCase;

    private static final String ORG_ID = "org-1";
    private static final Instant NOW = Instant.now();

    private Role defaultRole(String id, String name) {
        return new Role(id, null, name, name, true, null, List.of("AI_READ_OWN"), true, 0L, NOW, NOW);
    }

    private Role orgRole(String id, String name) {
        return new Role(id, ORG_ID, name, name, false, "role-admin", List.of("AZURE_READ", "REPOS_READ"), true, 0L, NOW, NOW);
    }

    // ── ListRolesUseCase ─────────────────────────────────────────────────────

    @Test
    void listRoles_shouldReturnDefaultAndOrgSpecificRoles() {
        var defaultRole = defaultRole("role-admin", "ADMIN");
        var orgRole = orgRole("role-custom", "My Custom Role");

        when(roleRepository.findAvailableForOrganization(ORG_ID))
                .thenReturn(List.of(defaultRole, orgRole));

        List<RoleOutput> result = listRolesUseCase.execute(ORG_ID);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).name()).isEqualTo("ADMIN");
        assertThat(result.get(0).isDefault()).isTrue();
        assertThat(result.get(1).name()).isEqualTo("My Custom Role");
        assertThat(result.get(1).isDefault()).isFalse();
    }

    @Test
    void listRoles_shouldReturnEmptyListWhenNoRoles() {
        when(roleRepository.findAvailableForOrganization(ORG_ID)).thenReturn(List.of());

        assertThat(listRolesUseCase.execute(ORG_ID)).isEmpty();
    }

    // ── CloneRoleUseCase ─────────────────────────────────────────────────────

    @Test
    void cloneRole_shouldCopyPermissionCodesAndSetClonedFromRoleId() {
        var source = defaultRole("role-admin", "ADMIN");
        var cloned = new Role("role-cloned", ORG_ID, "My Admin Clone", "My Admin Clone", false,
                "role-admin", List.of("AI_READ_OWN"), true, 0L, NOW, NOW);

        when(roleRepository.findById("role-admin")).thenReturn(Optional.of(source));
        when(roleRepository.save(any(Role.class))).thenReturn(cloned);

        RoleOutput output = cloneRoleUseCase.execute(new CloneRoleInput("role-admin", "My Admin Clone"), ORG_ID);

        assertThat(output.id()).isEqualTo("role-cloned");
        assertThat(output.isDefault()).isFalse();
        assertThat(output.organizationId()).isEqualTo(ORG_ID);
        assertThat(output.permissionCodes()).containsExactly("AI_READ_OWN");
    }

    @Test
    void cloneRole_shouldNotCopyNonAssignableOrganizationDelete() {
        var source = new Role("role-admin", null, "ADMIN", "ADMIN", true, null,
                new java.util.ArrayList<>(List.of("USER_MANAGE", "ORGANIZATION_DELETE")), true, 0L, NOW, NOW);
        when(roleRepository.findById("role-admin")).thenReturn(Optional.of(source));
        when(roleRepository.save(any(Role.class))).thenAnswer(inv -> inv.getArgument(0));

        RoleOutput output = cloneRoleUseCase.execute(new CloneRoleInput("role-admin", "Copy"), ORG_ID);

        assertThat(output.permissionCodes()).containsExactly("USER_MANAGE");
    }

    @Test
    void cloneRole_shouldThrowRoleNotFoundWhenSourceDoesNotExist() {
        when(roleRepository.findById("nonexistent")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cloneRoleUseCase.execute(new CloneRoleInput("nonexistent", "Clone"), ORG_ID))
                .isInstanceOf(RoleNotFoundException.class)
                .hasMessageContaining("nonexistent");
    }
}
