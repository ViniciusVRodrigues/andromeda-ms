package andromeda.sei.ms.feature.role.domain;

import java.util.List;
import java.util.Optional;

public interface RoleRepository {

    Optional<Role> findById(String id);

    List<Role> findAvailableForOrganization(String organizationId);

    Optional<Role> findGlobalByName(String name);

    Optional<Role> findByNameAndOrganizationId(String name, String organizationId);

    Role save(Role role);
}
