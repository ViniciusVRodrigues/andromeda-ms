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

@Document(collection = "work_item_transitions")
@CompoundIndexes({
        @CompoundIndex(def = "{'workItemId': 1, 'occurredAt': 1, 'toRawStatus': 1}", unique = true),
        @CompoundIndex(def = "{'organizationId': 1, 'workItemId': 1}")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkItemTransitionDocument {

    @Id
    private String id;

    private String organizationId;
    private String workItemId;
    private String fromRawStatus;
    private String toRawStatus;
    private WorkItem.ResolvedStatus toResolvedStatus;
    private Instant occurredAt;
    private Instant recordedAt;
    private boolean active = true;
    private Instant createdAt;
    private Instant updatedAt;

    @Version
    private Long version;
}
