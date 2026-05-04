package andromeda.sei.ms.feature.role.application.port.in;

import java.util.UUID;

public interface DeleteRoleUseCase {
    void execute(UUID roleId, UUID organizationId);
}
