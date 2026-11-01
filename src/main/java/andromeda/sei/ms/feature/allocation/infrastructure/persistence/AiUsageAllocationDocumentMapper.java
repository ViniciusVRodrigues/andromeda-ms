package andromeda.sei.ms.feature.allocation.infrastructure.persistence;

import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation;
import andromeda.sei.ms.feature.allocation.domain.AllocationSplit;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AiUsageAllocationDocumentMapper {

    public AiUsageAllocation toDomain(AiUsageAllocationDocument doc) {
        List<AllocationSplit> splits = doc.getSplits() == null ? List.of() :
                doc.getSplits().stream()
                        .map(s -> new AllocationSplit(s.getKind(), s.getWorkItemId(), s.getWorkItemTitle(), s.getPercentage(),
                                s.getCategory(), s.getNote()))
                        .toList();

        AiUsageAllocation allocation = new AiUsageAllocation(
                doc.getId(),
                doc.getOrganizationId(),
                doc.getUserId(),
                doc.getDay(),
                doc.getAiUsageIds() != null ? doc.getAiUsageIds() : List.of(),
                splits,
                doc.getStatus(),
                doc.getAssignedBy(),
                doc.isActive(),
                doc.getVersion(),
                doc.getCreatedAt(),
                doc.getUpdatedAt(),
                doc.getSuggestionSource()
        );
        if (doc.getAgent() != null) {
            allocation.setAgent(new AiUsageAllocation.AgentInfo(doc.getAgent().getResolvedBy(),
                    doc.getAgent().getRepositories() == null ? List.of() : doc.getAgent().getRepositories(),
                    doc.getAgent().getBranches() == null ? List.of() : doc.getAgent().getBranches(), doc.getAgent().getOverriddenTaskRef()));
        }
        allocation.setReviewReasons(doc.getReviewReasons() == null ? List.of() : doc.getReviewReasons().stream()
                .map(r -> new AiUsageAllocation.ReviewReason(r.getCode(), r.getWorkItemId(), r.getDisplayId(), r.getRatio(), r.getNote()))
                .toList());
        allocation.setReviewAcknowledgedAt(doc.getReviewAcknowledgedAt());
        allocation.setMovedFromAgent(doc.isMovedFromAgent());
        allocation.setApproval(toApproval(doc.getApproval()));
        return allocation;
    }

    private AiUsageAllocation.Approval toApproval(AiUsageAllocationDocument.ApprovalDocument a) {
        if (a == null) return null;
        List<AiUsageAllocation.ApprovalReason> reasons = a.getReasons() == null ? List.of() : a.getReasons().stream()
                .map(r -> new AiUsageAllocation.ApprovalReason(r.getCode(), r.getRepository(), r.getProjectId(),
                        r.getProjectName(), r.getWorkItemId(), r.getDisplayId(), r.getCapPercent(), r.getBaseUsd(),
                        r.getBaseMonth(), r.getUsedUsd(), r.getThisUsd(), r.getAfterPercent()))
                .toList();
        return new AiUsageAllocation.Approval(reasons, a.getApproverProjectIds(), a.getRequestedAt(),
                a.getDecision(), a.getDecidedBy(), a.getDecidedAt(), a.getNote());
    }

    private AiUsageAllocationDocument.ApprovalDocument toApprovalDocument(AiUsageAllocation.Approval a) {
        if (a == null) return null;
        List<AiUsageAllocationDocument.ApprovalReasonDocument> reasons = a.reasons().stream()
                .map(r -> new AiUsageAllocationDocument.ApprovalReasonDocument(r.code(), r.repository(),
                        r.projectId(), r.projectName(), r.workItemId(), r.displayId(), r.capPercent(), r.baseUsd(),
                        r.baseMonth(), r.usedUsd(), r.thisUsd(), r.afterPercent()))
                .toList();
        return new AiUsageAllocationDocument.ApprovalDocument(reasons, a.approverProjectIds(), a.requestedAt(),
                a.decision(), a.decidedBy(), a.decidedAt(), a.note());
    }

    public AiUsageAllocationDocument toDocument(AiUsageAllocation allocation) {
        List<AllocationSplitDocument> splitDocs = allocation.getSplits() == null ? List.of() :
                allocation.getSplits().stream()
                        .map(s -> AllocationSplitDocument.builder()
                                .kind(s.isOutOfDelivery() ? s.getKind() : null)
                                .category(s.isOutOfDelivery() ? s.getCategory() : null)
                                .note(s.isOutOfDelivery() ? s.getNote() : null)
                                .workItemId(s.getWorkItemId())
                                .workItemTitle(s.getWorkItemTitle())
                                .percentage(s.getPercentage())
                                .build())
                        .toList();

        return AiUsageAllocationDocument.builder()
                .id(allocation.getId())
                .organizationId(allocation.getOrganizationId())
                .userId(allocation.getUserId())
                .day(allocation.getDay())
                .aiUsageIds(allocation.getAiUsageIds())
                .splits(splitDocs)
                .status(allocation.getStatus())
                .assignedBy(allocation.getAssignedBy())
                .suggestionSource(allocation.getSuggestionSource())
                .agent(allocation.getAgent() == null ? null : new AiUsageAllocationDocument.AgentInfoDocument(
                        allocation.getAgent().resolvedBy(), allocation.getAgent().repositories(),
                        allocation.getAgent().branches(), allocation.getAgent().overriddenTaskRef()))
                .reviewReasons(allocation.getReviewReasons() == null ? List.of() : allocation.getReviewReasons().stream()
                        .map(r -> new AiUsageAllocationDocument.ReviewReasonDocument(r.code(), r.workItemId(),
                                r.displayId(), r.ratio(), r.note()))
                        .toList())
                .reviewAcknowledgedAt(allocation.getReviewAcknowledgedAt())
                .movedFromAgent(allocation.isMovedFromAgent())
                .approval(toApprovalDocument(allocation.getApproval()))
                .active(allocation.isActive())
                .version(allocation.getVersion())
                .createdAt(allocation.getCreatedAt())
                .updatedAt(allocation.getUpdatedAt())
                .build();
    }
}
