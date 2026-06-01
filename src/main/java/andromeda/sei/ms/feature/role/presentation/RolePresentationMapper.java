package andromeda.sei.ms.feature.role.presentation;

import andromeda.sei.ms.feature.role.application.dto.RoleOutput;
import andromeda.sei.ms.feature.role.presentation.dto.RoleResponse;
import org.springframework.stereotype.Component;

@Component
public class RolePresentationMapper {

    public RoleResponse toResponse(RoleOutput output) {
        return new RoleResponse(
                output.id(),
                output.name(),
                output.organizationId(),
                output.isDefault(),
                output.permissionCodes()
        );
    }
}
