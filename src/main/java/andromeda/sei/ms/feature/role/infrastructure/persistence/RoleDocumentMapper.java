package andromeda.sei.ms.feature.role.infrastructure.persistence;

import andromeda.sei.ms.feature.role.domain.Role;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class RoleDocumentMapper {

    public Role toDomain(RoleDocument doc) {
        return new Role(
                doc.getId(),
                doc.getOrganizationId(),
                doc.getName(),
                doc.isDefault(),
                doc.getClonedFromRoleId(),
                doc.getPermissionCodes() != null ? new ArrayList<>(doc.getPermissionCodes()) : new ArrayList<>(),
                doc.isActive(),
                doc.getVersion() != null ? doc.getVersion() : 0L,
                doc.getCreatedAt(),
                doc.getUpdatedAt()
        );
    }

    public RoleDocument toDocument(Role role) {
        return RoleDocument.builder()
                .id(role.getId())
                .organizationId(role.getOrganizationId())
                .name(role.getName())
                .isDefault(role.isDefault())
                .clonedFromRoleId(role.getClonedFromRoleId())
                .permissionCodes(role.getPermissionCodes() != null ? new ArrayList<>(role.getPermissionCodes()) : List.of())
                .active(role.isActive())
                .createdAt(role.getCreatedAt())
                .updatedAt(role.getUpdatedAt())
                .build();
    }
}
