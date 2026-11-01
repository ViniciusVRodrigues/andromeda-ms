package andromeda.sei.ms.feature.allocation.presentation.dto;

import andromeda.sei.ms.feature.allocation.application.dto.ApprovalQueueItemOutput;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** A day waiting for the caller approval (GET /api/v1/ai/allocations/approvals). */
public record ApprovalQueueItemResponse(String allocationId, String userId, String userName, LocalDate day,
                                        BigDecimal costUsd, long tokens, List<SplitResponse> splits,
                                        List<ApprovalReasonResponse> reasons, Instant requestedAt,
                                        List<Repository> repositories) {

    public record Repository(String name, String branch) {}

    public static ApprovalQueueItemResponse from(ApprovalQueueItemOutput o) {
        return new ApprovalQueueItemResponse(o.allocationId(), o.userId(), o.userName(), o.day(), o.costUsd(),
                o.tokens(),
                o.splits().stream().map(s -> new SplitResponse(s.kind(), s.workItemId(), s.workItemTitle(),
                        s.percentage(), s.category(), s.note())).toList(),
                o.reasons().stream().map(ApprovalReasonResponse::from).toList(), o.requestedAt(),
                o.repositories().stream().map(r -> new Repository(r.name(), r.branch())).toList());
    }
}
