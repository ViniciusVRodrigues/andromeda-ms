package andromeda.sei.ms.feature.allocation.application.dto;

import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation;

import java.time.Instant;
import java.util.List;

/**
 * {@code state} is SUGGESTED, NEEDS_REVIEW or AWAITING_APPROVAL; reasons are the distinct codes (the approval
 * reason codes when awaiting), details the union of the day's review reasons ({@code note} and
 * {@code decidedByName} fill the RETURNED one). {@code approval} is present only while awaiting.
 */
public record ReviewOutput(String state, List<String> reasons, List<Detail> details, Approval approval) {

    public ReviewOutput(String state, List<String> reasons, List<Detail> details) {
        this(state, reasons, details, null);
    }

    public record Detail(String code, String workItemId, String displayId, Long ratio, String note,
                         String decidedByName) {
        public Detail(String code, String workItemId, String displayId, Long ratio) {
            this(code, workItemId, displayId, ratio, null, null);
        }
    }

    public record Approval(List<AiUsageAllocation.ApprovalReason> reasons, List<String> approverProjectIds,
                           Instant requestedAt) {}
}
