package andromeda.sei.ms.feature.role.application.usecase;

import andromeda.sei.ms.feature.role.application.dto.CloneRoleInput;
import andromeda.sei.ms.feature.role.application.dto.CreateRoleInput;
import andromeda.sei.ms.feature.role.domain.Role;
import andromeda.sei.ms.feature.role.domain.RoleRepository;
import andromeda.sei.ms.feature.role.domain.exception.RoleDuplicateNameException;
import andromeda.sei.ms.feature.role.domain.exception.RoleInvalidNameException;
import andromeda.sei.ms.feature.role.domain.exception.RoleNotFoundException;
import andromeda.sei.ms.feature.role.domain.exception.RolePermissionNotAssignableException;
import andromeda.sei.ms.shared.application.security.RoleChanged;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** ROL1: creating or cloning a role answers 2xx or a 4xx domain error, never a 500. */
@ExtendWith(MockitoExtension.class)
class CreateAndCloneRoleTest {

    private static final String ORG = "org-1";
    private static final Instant NOW = Instant.now();

    @Mock private RoleRepository roleRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    private CreateRoleUseCase create() { return new CreateRoleUseCase(roleRepository, eventPublisher); }
    private CloneRoleUseCase cloner() { return new CloneRoleUseCase(roleRepository, eventPublisher); }

    private void saveEchoes() {
        when(roleRepository.save(any())).thenAnswer(inv -> {
            Role r = inv.getArgument(0);
            r.setId("new-role");
            return r;
        });
    }

    @Test
    void create_shouldSaveTrimmedRoleAndPublishRoleChanged() {
        saveEchoes();

        var out = create().execute(new CreateRoleInput("  QA Lead ", List.of("AZURE_READ", "GITHUB_READ", "AZURE_READ")), ORG);

        assertThat(out.name()).isEqualTo("QA Lead");
        assertThat(out.isDefault()).isFalse();
        assertThat(out.permissionCodes()).containsExactly("AZURE_READ", "REPOS_READ");
        verify(eventPublisher).publishEvent(new RoleChanged("new-role", ORG));
    }

    @Test
    void create_shouldAcceptNullPermissionList() {
        saveEchoes();

        assertThat(create().execute(new CreateRoleInput("Empty", null), ORG).permissionCodes()).isEmpty();
    }

    @Test
    void create_shouldRejectBlankOrTooLongName() {
        assertThatThrownBy(() -> create().execute(new CreateRoleInput("  ", List.of()), ORG))
                .isInstanceOf(RoleInvalidNameException.class);
        assertThatThrownBy(() -> create().execute(new CreateRoleInput(null, List.of()), ORG))
                .isInstanceOf(RoleInvalidNameException.class);
        assertThatThrownBy(() -> create().execute(new CreateRoleInput("x".repeat(61), List.of()), ORG))
                .isInstanceOf(RoleInvalidNameException.class);
        verify(roleRepository, never()).save(any());
    }

    @Test
    void create_shouldRejectDuplicateNameInOrganization() {
        when(roleRepository.findByNameAndOrganizationId("Dup", ORG)).thenReturn(Optional.of(
                new Role("r", ORG, "Dup", "Dup", false, null, List.of(), true, 0L, NOW, NOW)));

        assertThatThrownBy(() -> create().execute(new CreateRoleInput("Dup", List.of()), ORG))
                .isInstanceOf(RoleDuplicateNameException.class);
        verify(roleRepository, never()).save(any());
    }

    @Test
    void create_shouldRejectNameOfDefaultRole() {
        when(roleRepository.findGlobalByName("ADMIN")).thenReturn(Optional.of(
                new Role("g", null, "ADMIN", "ADMIN", true, null, List.of(), true, 0L, NOW, NOW)));

        assertThatThrownBy(() -> create().execute(new CreateRoleInput("ADMIN", List.of()), ORG))
                .isInstanceOf(RoleDuplicateNameException.class);
    }

    @Test
    void create_shouldRejectUnknownNonAssignableAndNullPermissionCodes() {
        assertThatThrownBy(() -> create().execute(new CreateRoleInput("A", List.of("NOPE")), ORG))
                .isInstanceOf(RolePermissionNotAssignableException.class);
        assertThatThrownBy(() -> create().execute(new CreateRoleInput("B", List.of("ORGANIZATION_DELETE")), ORG))
                .isInstanceOf(RolePermissionNotAssignableException.class);
        assertThatThrownBy(() -> create().execute(new CreateRoleInput("C", Arrays.asList("AZURE_READ", null)), ORG))
                .isInstanceOf(RolePermissionNotAssignableException.class);
        verify(roleRepository, never()).save(any());
    }

    @Test
    void clone_shouldRejectDuplicateAndBlankNames() {
        when(roleRepository.findById("src")).thenReturn(Optional.of(
                new Role("src", null, "DEV", "DEV", true, null, List.of("AZURE_READ"), true, 0L, NOW, NOW)));
        when(roleRepository.findByNameAndOrganizationId("Dup", ORG)).thenReturn(Optional.of(
                new Role("r", ORG, "Dup", "Dup", false, null, List.of(), true, 0L, NOW, NOW)));

        assertThatThrownBy(() -> cloner().execute(new CloneRoleInput("src", "Dup"), ORG))
                .isInstanceOf(RoleDuplicateNameException.class);
        assertThatThrownBy(() -> cloner().execute(new CloneRoleInput("src", " "), ORG))
                .isInstanceOf(RoleInvalidNameException.class);
        assertThatThrownBy(() -> cloner().execute(new CloneRoleInput("src", null), ORG))
                .isInstanceOf(RoleInvalidNameException.class);
    }

    @Test
    void clone_shouldNotSeeRoleOfAnotherOrganization() {
        when(roleRepository.findById("foreign")).thenReturn(Optional.of(
                new Role("foreign", "org-2", "X", "X", false, null, List.of("AZURE_READ"), true, 0L, NOW, NOW)));

        assertThatThrownBy(() -> cloner().execute(new CloneRoleInput("foreign", "Copy"), ORG))
                .isInstanceOf(RoleNotFoundException.class);
        assertThatThrownBy(() -> cloner().execute(new CloneRoleInput(null, "Copy"), ORG))
                .isInstanceOf(RoleNotFoundException.class);
    }

    @Test
    void clone_shouldPublishRoleChanged() {
        when(roleRepository.findById("src")).thenReturn(Optional.of(
                new Role("src", null, "DEV", "DEV", true, null, List.of("AZURE_READ"), true, 0L, NOW, NOW)));
        saveEchoes();

        cloner().execute(new CloneRoleInput("src", "Copy"), ORG);

        verify(eventPublisher).publishEvent(new RoleChanged("new-role", ORG));
    }
}
