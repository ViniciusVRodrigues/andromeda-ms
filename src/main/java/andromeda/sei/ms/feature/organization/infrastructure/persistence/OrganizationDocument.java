package andromeda.sei.ms.feature.organization.infrastructure.persistence;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Document(collection = "organizations")
@CompoundIndex(def = "{'slug': 1}", unique = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrganizationDocument {

    @Id
    private String id;

    private String name;
    private String slug;
    private String sector;
    private String country;
    private List<String> seniorities;
    private List<AiPriceEntryDocument> aiPrices;
    private boolean active = true;

    @Version
    private Long version;

    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();
}
