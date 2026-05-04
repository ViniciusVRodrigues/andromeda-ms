package andromeda.sei.ms.feature.role.application.port.in;

import andromeda.sei.ms.feature.role.adapter.in.web.dto.UpdateRolePermissionsRequest;
import andromeda.sei.ms.feature.role.domain.Role;
import java.util.UUID;

public interface UpdateRolePermissionsUseCase {
    Role execute(UUID roleId, UpdateRolePermissionsRequest request, UUID organizationId);
}
