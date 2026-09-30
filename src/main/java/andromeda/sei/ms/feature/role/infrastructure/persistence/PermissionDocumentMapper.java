package andromeda.sei.ms.feature.role.infrastructure.persistence;

import andromeda.sei.ms.feature.role.domain.Permission;
import andromeda.sei.ms.shared.domain.security.PermissionScope;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class PermissionDocumentMapper {

    public Permission toDomain(PermissionDocument doc) {
        return doc.getScope() == null
                ? new Permission(doc.getId(), doc.getCode(), doc.getDescription(), doc.getModule())
                : new Permission(doc.getId(), doc.getCode(), doc.getDescription(), doc.getModule(),
                        PermissionScope.valueOf(doc.getScope()));
    }

    public PermissionDocument toDocument(Permission permission) {
        Instant now = Instant.now();
        var builder = PermissionDocument.builder()
                .code(permission.getCode())
                .description(permission.getDescription())
                .module(permission.getModule())
                .scope(permission.getScope() != null ? permission.getScope().name() : null)
                .active(true)
                .createdAt(now)
                .updatedAt(now);

        if (permission.getId() != null) {
            builder.id(permission.getId());
        }

        return builder.build();
    }
}
