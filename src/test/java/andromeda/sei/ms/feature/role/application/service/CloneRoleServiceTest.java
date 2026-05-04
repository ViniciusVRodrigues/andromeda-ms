package andromeda.sei.ms.feature.role.application.service;

import andromeda.sei.ms.feature.role.adapter.in.web.dto.CloneRoleRequest;
import andromeda.sei.ms.feature.role.application.port.out.RoleRepository;
import andromeda.sei.ms.feature.role.domain.Role;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CloneRoleServiceTest {

    @Mock RoleRepository roleRepository;
    @InjectMocks CloneRoleService service;

    @Test
    void shouldCloneRoleSuccessfully() {
        UUID sourceId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID newRoleId = UUID.randomUUID();

        Role source = new Role(sourceId, null, "DEVELOPER", true, null, List.of());
        Role cloned = new Role(newRoleId, orgId, "DEVELOPER_SENIOR", false, sourceId, List.of());
        CloneRoleRequest request = new CloneRoleRequest(sourceId, "DEVELOPER_SENIOR");

        when(roleRepository.findById(sourceId)).thenReturn(Optional.of(source));
        when(roleRepository.save(any())).thenReturn(cloned);

        Role result = service.execute(request, orgId);

        assertThat(result.name()).isEqualTo("DEVELOPER_SENIOR");
        assertThat(result.isDefault()).isFalse();
        assertThat(result.clonedFromRoleId()).isEqualTo(sourceId);
    }

    @Test
    void shouldThrowWhenSourceRoleNotFound() {
        UUID sourceId = UUID.randomUUID();
        CloneRoleRequest request = new CloneRoleRequest(sourceId, "NEW_ROLE");
        when(roleRepository.findById(sourceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.execute(request, UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
