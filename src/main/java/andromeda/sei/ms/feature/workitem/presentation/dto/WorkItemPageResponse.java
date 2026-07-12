package andromeda.sei.ms.feature.workitem.presentation.dto;

import andromeda.sei.ms.feature.workitem.domain.WorkItemPageResult;

import java.util.List;

public record WorkItemPageResponse(
        int page,
        int size,
        long totalElements,
        List<WorkItemSummaryResponse> content
) {
    public static WorkItemPageResponse from(WorkItemPageResult result) {
        return new WorkItemPageResponse(
                result.getPage(),
                result.getSize(),
                result.getTotalElements(),
                result.getContent().stream().map(WorkItemSummaryResponse::from).toList());
    }
}
