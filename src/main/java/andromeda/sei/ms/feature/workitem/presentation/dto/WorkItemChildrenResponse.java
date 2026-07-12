package andromeda.sei.ms.feature.workitem.presentation.dto;

import andromeda.sei.ms.feature.workitem.application.dto.WorkItemChildrenOutput;

import java.util.List;

public record WorkItemChildrenResponse(
        List<WorkItemSummaryResponse> content,
        boolean truncated
) {
    public static WorkItemChildrenResponse from(WorkItemChildrenOutput output) {
        return new WorkItemChildrenResponse(
                output.children().stream().map(WorkItemSummaryResponse::from).toList(),
                output.truncated());
    }
}
