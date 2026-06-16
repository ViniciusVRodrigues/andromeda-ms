package andromeda.sei.ms.feature.role.infrastructure.persistence;

import andromeda.sei.ms.feature.role.domain.Permission;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class PermissionDocumentMapper {

    public Permission toDomain(PermissionDocument doc) {
        return new Permission(doc.getId(), doc.getCode(), doc.getDescription(), doc.getModule());
    }

    public PermissionDocument toDocument(Permission permission) {
        Instant now = Instant.now();
        var builder = PermissionDocument.builder()
                .code(permission.getCode())
                .description(permission.getDescription())
                .module(permission.getModule())
                .active(true)
                .createdAt(now)
                .updatedAt(now);

        if (permission.getId() != null) {
            builder.id(permission.getId());
        }

        return builder.build();
    }
}
