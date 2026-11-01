package andromeda.sei.ms.feature.allocation.presentation.dto;

import java.util.List;

public record ApproveAllocationsResponse(List<String> approved, List<Skipped> skipped) {

    /** {@code reason}: FORBIDDEN | SELF | NOT_AWAITING | CONFLICT | NOT_FOUND; {@code decidedByName} only on NOT_AWAITING. */
    public record Skipped(String id, String reason, String decidedByName) {}
}
