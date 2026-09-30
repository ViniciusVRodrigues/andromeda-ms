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

@Document(collection = "sprint_metrics")
@CompoundIndexes({
        @CompoundIndex(def = "{'organizationId': 1, 'sprintId': 1, 'source': 1}", unique = true),
        @CompoundIndex(def = "{'organizationId': 1, 'projectId': 1}")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SprintMetricsDocument {

    @Id
    private String id;

    private String organizationId;
    private String projectId;
    private String sprintId;
    private WorkItem.Source source;
    private Double velocity;
    private Double avgCycleTime;
    private Double avgLeadTime;
    private Double plannedEffort;
    private Double cycleTimeP50;
    private Double cycleTimeP90;
    private Double leadTimeP50;
    private Double leadTimeP90;
    private Integer completedItemCount;
    private Integer totalItemCount;
    private Integer itemsWithPoints;
    private Instant calculatedAt;
    private boolean active = true;
    private Instant createdAt;
    private Instant updatedAt;

    @Version
    private Long version;
}
