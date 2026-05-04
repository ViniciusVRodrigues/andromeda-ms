package andromeda.sei.ms.feature.role.application.port.out;

import andromeda.sei.ms.feature.role.domain.Permission;
import java.util.List;
import java.util.Set;

public interface PermissionRepository {
    List<Permission> findByCodes(Set<String> codes);
}
