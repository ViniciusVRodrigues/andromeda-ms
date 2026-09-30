package andromeda.sei.ms.feature.role.infrastructure.persistence;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "permissions")
@CompoundIndex(def = "{'code': 1}", unique = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PermissionDocument {

    @Id
    private String id;

    private String code;
    private String description;
    private String module;
    /** {@code ORG_ONLY} or {@code PROJECT_SCOPABLE} (project-scoped-permissions, permission-scopes). */
    private String scope;
    private boolean active = true;

    @Version
    private Long version;

    private Instant createdAt;
    private Instant updatedAt;
}
