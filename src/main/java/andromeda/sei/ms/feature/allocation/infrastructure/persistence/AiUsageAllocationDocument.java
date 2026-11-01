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

import java.math.BigDecimal;
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
    private AiUsageAllocation.SuggestionSource suggestionSource;
    private AgentInfoDocument agent;
    private boolean movedFromAgent;
    /** Absent on documents written before the field existed; read as empty. */
    private List<ReviewReasonDocument> reviewReasons;
    private Instant reviewAcknowledgedAt;
    /** Absent until approval is asked for. */
    private ApprovalDocument approval;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApprovalReasonDocument {
        private AiUsageAllocation.ApprovalReasonCode code;
        private String repository;
        private String projectId;
        private String projectName;
        private String workItemId;
        private String displayId;
        private Integer capPercent;
        private BigDecimal baseUsd;
        private String baseMonth;
        private BigDecimal usedUsd;
        private BigDecimal thisUsd;
        private Integer afterPercent;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApprovalDocument {
        private List<ApprovalReasonDocument> reasons;
        private List<String> approverProjectIds;
        private Instant requestedAt;
        private AiUsageAllocation.ApprovalDecision decision;
        private String decidedBy;
        private Instant decidedAt;
        private String note;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReviewReasonDocument {
        private AiUsageAllocation.ReviewCode code;
        private String workItemId;
        private String displayId;
        private Long ratio;
        private String note;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AgentInfoDocument {
        private AiUsageAllocation.ResolvedBy resolvedBy;
        private List<String> repositories;
        private List<String> branches;
        private String overriddenTaskRef;
    }

    @lombok.Builder.Default
    private boolean active = true;

    @Version
    private Long version;

    private Instant createdAt;
    private Instant updatedAt;
}
