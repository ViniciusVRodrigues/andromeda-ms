package andromeda.sei.ms.feature.role.application.port.in;

import andromeda.sei.ms.feature.role.domain.Role;
import java.util.List;
import java.util.UUID;

public interface ListRolesUseCase {
    List<Role> execute(UUID organizationId);
}
