package andromeda.sei.ms.feature.role.domain;

import java.util.List;
import java.util.UUID;

public record Role(UUID id, UUID organizationId, String name, boolean isDefault, UUID clonedFromRoleId, List<Permission> permissions) {}
