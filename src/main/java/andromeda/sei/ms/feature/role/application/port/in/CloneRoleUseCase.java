package andromeda.sei.ms.feature.role.application.port.in;

import andromeda.sei.ms.feature.role.adapter.in.web.dto.CloneRoleRequest;
import andromeda.sei.ms.feature.role.domain.Role;
import java.util.UUID;

public interface CloneRoleUseCase {
    Role execute(CloneRoleRequest request, UUID organizationId);
}
