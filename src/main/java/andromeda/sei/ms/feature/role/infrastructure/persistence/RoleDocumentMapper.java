package andromeda.sei.ms.feature.role.infrastructure.persistence;

import andromeda.sei.ms.feature.role.domain.Role;
import andromeda.sei.ms.shared.domain.security.PermissionAliasResolver;
import org.springframework.stereotype.Component;

import java.util.ArrayList;

/**
 * Choke point of the rollback-safe {@code GITHUB_*} -> {@code REPOS_*} rename (see
 * {@link PermissionAliasResolver}): the domain {@link Role} only ever holds current codes
 * ({@code toDomain} strips the legacy mirrors), while the stored document keeps each renamed code
 * paired with its legacy mirror ({@code toDocument}) so a rolled-back deploy still grants access.
 */
@Component
public class RoleDocumentMapper {

    public Role toDomain(RoleDocument doc) {
        return new Role(
                doc.getId(),
                doc.getOrganizationId(),
                doc.getName(),
                doc.getLabel(),
                doc.isDefault(),
                doc.getClonedFromRoleId(),
                new ArrayList<>(PermissionAliasResolver.canonicalize(doc.getPermissionCodes())),
                doc.isActive(),
                doc.getVersion(),
                doc.getCreatedAt(),
                doc.getUpdatedAt()
        );
    }

    public RoleDocument toDocument(Role role) {
        var builder = RoleDocument.builder()
                .organizationId(role.getOrganizationId())
                .name(role.getName())
                .label(role.getLabel())
                .isDefault(role.isDefault())
                .clonedFromRoleId(role.getClonedFromRoleId())
                .permissionCodes(new ArrayList<>(PermissionAliasResolver.withLegacyMirrors(role.getPermissionCodes())))
                .active(role.isActive())
                .createdAt(role.getCreatedAt())
                .updatedAt(role.getUpdatedAt());

        if (role.getId() != null) {
            builder.id(role.getId());
            builder.version(role.getVersion());
        }

        return builder.build();
    }
}
