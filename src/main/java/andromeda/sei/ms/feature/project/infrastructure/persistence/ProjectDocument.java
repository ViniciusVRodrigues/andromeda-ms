package andromeda.sei.ms.feature.project.infrastructure.persistence;

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
import java.util.ArrayList;
import java.util.List;

@Document(collection = "projects")
@CompoundIndexes({
        @CompoundIndex(def = "{'organizationId': 1, 'slug': 1}", unique = true),
        @CompoundIndex(def = "{'organizationId': 1}"),
        @CompoundIndex(def = "{'organizationId': 1, 'memberIds': 1}", sparse = true)
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectDocument {

    @Id
    private String id;

    private String organizationId;
    private String name;
    private String slug;
    private String description;
    private boolean active = true;

    @Builder.Default
    private List<String> memberIds = new ArrayList<>();

    @Version
    private Long version;

    private Instant createdAt;
    private Instant updatedAt;
}
