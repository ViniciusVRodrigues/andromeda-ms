package andromeda.sei.ms.feature.workitem.infrastructure.persistence;

import andromeda.sei.ms.feature.workitem.domain.Sprint;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
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

@Document(collection = "sprints")
@CompoundIndexes({
        @CompoundIndex(def = "{'organizationId': 1, 'externalId': 1, 'teamId': 1}", unique = true),
        @CompoundIndex(def = "{'organizationId': 1, 'projectId': 1}")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SprintDocument {

    @Id
    private String id;

    private String organizationId;
    private String projectId;
    private WorkItem.Source source;
    private String externalId;
    private String teamId;
    private String teamName;
    private String name;
    private String path;
    private Instant startDate;
    private Instant endDate;
    private Sprint.TimeFrame timeFrame;
    private boolean active = true;
    private Instant createdAt;
    private Instant updatedAt;

    @Version
    private Long version;
}
