package andromeda.sei.ms.feature.workitem.infrastructure.persistence;

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
import java.time.LocalDate;
import java.util.List;

@Document(collection = "work_item_activities")
@CompoundIndexes({
        @CompoundIndex(name = "org_workitem_revision_uq",
                def = "{'organizationId': 1, 'workItemId': 1, 'revision': 1}", unique = true),
        @CompoundIndex(name = "org_actor_day", def = "{'organizationId': 1, 'actorUserId': 1, 'day': 1}")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkItemActivityDocument {

    @Id
    private String id;

    private String organizationId;
    private String workItemId;
    private String externalId;
    private int revision;
    private String actorExternalId;
    private String actorUniqueName;
    private String actorUserId;
    private Instant changedAt;
    private LocalDate day;
    private List<String> changedFields;
    @Builder.Default
    private boolean active = true;
    private Instant createdAt;
    private Instant updatedAt;

    @Version
    private Long version;
}
