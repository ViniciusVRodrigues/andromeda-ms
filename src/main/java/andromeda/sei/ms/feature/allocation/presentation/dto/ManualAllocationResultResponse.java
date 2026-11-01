package andromeda.sei.ms.feature.allocation.presentation.dto;

/** Answer of the manual allocation routes: the allocation, where it landed, and the request when it awaits. */
public record ManualAllocationResultResponse(String allocationId, String status, ApprovalResponse approval) {
}
