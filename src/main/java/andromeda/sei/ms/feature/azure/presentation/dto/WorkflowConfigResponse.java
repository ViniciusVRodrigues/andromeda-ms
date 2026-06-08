package andromeda.sei.ms.feature.azure.presentation.dto;

import andromeda.sei.ms.feature.workitem.domain.WorkItem;

import java.util.List;

public record WorkflowConfigResponse(
        List<StatusMappingResponse> statusMappings,
        List<TypeMappingResponse> typeMappings,
        String configSource
) {
    public record StatusMappingResponse(String rawStatus, WorkItem.ResolvedStatus resolvedStatus) {}
    public record TypeMappingResponse(String rawType, WorkItem.ResolvedType resolvedType) {}
}
