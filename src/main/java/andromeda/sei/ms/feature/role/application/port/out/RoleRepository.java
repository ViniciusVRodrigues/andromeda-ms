package andromeda.sei.ms.feature.role.application.port.out;

import andromeda.sei.ms.feature.role.domain.Role;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoleRepository {
    List<Role> findAvailableForOrganization(UUID organizationId);
    Optional<Role> findById(UUID id);
    Optional<Role> findDefaultByName(String name);
    Role save(Role role);
    void delete(UUID roleId);
    boolean hasAssignedUsers(UUID roleId);
    List<String> findPermissionCodesByRoleId(UUID roleId);
}
