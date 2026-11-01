package andromeda.sei.ms.feature.allocation.application.usecase;

import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation;
import andromeda.sei.ms.feature.allocation.domain.AllocationSplit;
import andromeda.sei.ms.shared.domain.security.UserPermissionSnapshot;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/** Builders shared by the approval queue tests (uso-ia-fora-de-entrega P3). */
final class ApprovalQueueTestData {

    static final String ORG = "org-1";
    static final String CODE = "AI_ALLOCATION_APPROVE";

    private ApprovalQueueTestData() {}

    /** A caller whose role carries the code at organization level when {@code orgLevel}. */
    static UserPermissionSnapshot caller(String userId, boolean orgLevel) {
        return new UserPermissionSnapshot(ORG, userId, "role", "TECH_LEAD",
                orgLevel ? List.of(CODE) : List.of(), List.of(), Set.of(), 1L);
    }

    static AiUsageAllocation awaiting(String id, String ownerId, List<String> approverProjects, Instant requestedAt) {
        AiUsageAllocation a = new AiUsageAllocation(id, ORG, ownerId, LocalDate.of(2026, 10, 28),
                List.of("u-" + id), List.of(AllocationSplit.outOfDelivery(BigDecimal.ONE,
                        AllocationSplit.Category.STUDY, null)),
                AiUsageAllocation.Status.AWAITING_APPROVAL, AiUsageAllocation.AssignedBy.MANUAL, true, 3L,
                requestedAt, requestedAt);
        a.setApproval(new AiUsageAllocation.Approval(
                List.of(new AiUsageAllocation.ApprovalReason(AiUsageAllocation.ApprovalReasonCode.REPO_OTHER_PROJECT,
                        "org/repo", "p-1", "Andromeda", null, null, null, null, null, null, null, null)),
                approverProjects, requestedAt, null, null, null, null));
        return a;
    }
}
