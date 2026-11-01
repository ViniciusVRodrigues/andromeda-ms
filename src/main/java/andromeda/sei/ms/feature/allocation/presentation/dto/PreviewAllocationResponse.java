package andromeda.sei.ms.feature.allocation.presentation.dto;

import java.util.List;

public record PreviewAllocationResponse(boolean needsApproval, List<ApprovalReasonResponse> reasons) {
}
