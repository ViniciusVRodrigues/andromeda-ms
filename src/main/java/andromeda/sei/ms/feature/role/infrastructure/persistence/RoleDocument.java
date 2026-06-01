package andromeda.sei.ms.feature.role.infrastructure.persistence;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Document(collection = "roles")
@CompoundIndexes({
        @CompoundIndex(def = "{'organizationId': 1, 'active': 1}"),
        @CompoundIndex(def = "{'organizationId': 1, 'name': 1}", unique = true)
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleDocument {

    @Id
    private String id;

    private String organizationId;
    private String name;
    private boolean isDefault;
    private String clonedFromRoleId;
    private List<String> permissionCodes;
    private boolean active = true;

    @Version
    private Long version;

    private Instant createdAt;
    private Instant updatedAt;
}
