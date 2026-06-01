package andromeda.sei.ms.feature.role.presentation.dto;

import java.util.List;

public record UpdateRolePermissionsRequest(List<String> add, List<String> remove) {
}
