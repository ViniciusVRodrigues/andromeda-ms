package andromeda.sei.ms.feature.allocation.infrastructure.persistence;

import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation;
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

@Document(collection = "ai_usage_allocations")
@CompoundIndexes({
        @CompoundIndex(def = "{'organizationId':1,'userId':1,'status':1}", name = "idx_allocation_org_user_status"),
        @CompoundIndex(def = "{'organizationId':1,'userId':1,'day':1}", name = "idx_allocation_org_user_day")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiUsageAllocationDocument {

    @Id
    private String id;

    private String organizationId;
    private String userId;
    private LocalDate day;

    private List<String> aiUsageIds;
    private List<AllocationSplitDocument> splits;

    private AiUsageAllocation.Status status;
    private AiUsageAllocation.AssignedBy assignedBy;

    @lombok.Builder.Default
    private boolean active = true;

    @Version
    private Long version;

    private Instant createdAt;
    private Instant updatedAt;
}
