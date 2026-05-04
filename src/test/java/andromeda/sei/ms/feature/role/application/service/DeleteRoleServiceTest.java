package andromeda.sei.ms.feature.role.application.service;

import andromeda.sei.ms.feature.role.application.port.out.RoleRepository;
import andromeda.sei.ms.feature.role.domain.Role;
import andromeda.sei.ms.shared.exception.BusinessException;
import andromeda.sei.ms.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeleteRoleServiceTest {

    @Mock RoleRepository roleRepository;
    @InjectMocks DeleteRoleService service;

    @Test
    void shouldDeleteCustomRoleSuccessfully() {
        UUID roleId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        Role role = new Role(roleId, orgId, "CUSTOM_ROLE", false, null, List.of());

        when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));
        when(roleRepository.hasAssignedUsers(roleId)).thenReturn(false);

        service.execute(roleId, orgId);

        verify(roleRepository).delete(roleId);
    }

    @Test
    void shouldThrowWhenDeletingDefaultRole() {
        UUID roleId = UUID.randomUUID();
        Role role = new Role(roleId, null, "ADMIN", true, null, List.of());
        when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));

        assertThatThrownBy(() -> service.execute(roleId, UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("default role");
    }

    @Test
    void shouldThrowWhenRoleHasAssignedUsers() {
        UUID roleId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        Role role = new Role(roleId, orgId, "CUSTOM_ROLE", false, null, List.of());

        when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));
        when(roleRepository.hasAssignedUsers(roleId)).thenReturn(true);

        assertThatThrownBy(() -> service.execute(roleId, orgId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("assigned users");
    }
}
