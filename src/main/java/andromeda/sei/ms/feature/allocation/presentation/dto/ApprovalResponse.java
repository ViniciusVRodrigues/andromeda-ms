package andromeda.sei.ms.feature.allocation.presentation.dto;

import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation;

import java.time.Instant;
import java.util.List;

/** The open approval request of an allocation. */
public record ApprovalResponse(List<ApprovalReasonResponse> reasons, List<String> approverProjectIds,
                               Instant requestedAt) {

    public static ApprovalResponse from(AiUsageAllocation.Approval a) {
        if (a == null) return null;
        return new ApprovalResponse(a.reasons().stream().map(ApprovalReasonResponse::from).toList(),
                a.approverProjectIds(), a.requestedAt());
    }
}
