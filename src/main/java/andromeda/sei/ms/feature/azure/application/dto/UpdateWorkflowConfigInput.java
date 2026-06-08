package andromeda.sei.ms.feature.azure.application.dto;

import andromeda.sei.ms.feature.workitem.domain.WorkItem;

import java.util.List;

public record UpdateWorkflowConfigInput(
        List<StatusMappingInput> statusMappings,
        List<TypeMappingInput> typeMappings
) {
    public record StatusMappingInput(String rawStatus, WorkItem.ResolvedStatus resolvedStatus) {}
    public record TypeMappingInput(String rawType, WorkItem.ResolvedType resolvedType) {}
}
