package andromeda.sei.ms.feature.workitem.infrastructure.persistence;

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
import java.util.List;

@Document(collection = "work_items")
@CompoundIndexes({
        @CompoundIndex(def = "{'organizationId': 1, 'externalId': 1, 'source': 1}", unique = true),
        @CompoundIndex(def = "{'organizationId': 1, 'projectId': 1}")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkItemDocument {

    @Id
    private String id;

    private String organizationId;
    private String projectId;
    private WorkItem.Source source;
    private String externalId;
    private Instant externalUpdatedAt;
    private String title;
    private String rawType;
    private WorkItem.ResolvedType resolvedType;
    private String rawStatus;
    private WorkItem.ResolvedStatus resolvedStatus;
    private String priority;
    private Double storyPoints;
    private List<String> labels;
    private String sprintId;
    private String assigneeExternalId;
    private String assigneeUserId;
    private String assigneeEmail;
    private String parentExternalId;
    private Instant createdAt;
    private Instant startedAt;
    private Instant completedAt;
    private Double cycleTimeHours;
    private Double leadTimeHours;
    private Double tokensPerSp;
    private boolean active = true;
    private Instant updatedAt;

    @Version
    private Long version;
}
