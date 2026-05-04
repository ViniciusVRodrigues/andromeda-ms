package andromeda.sei.ms.feature.role.adapter.in.web.dto;

import java.util.Set;

public record UpdateRolePermissionsRequest(Set<String> add, Set<String> remove) {}
