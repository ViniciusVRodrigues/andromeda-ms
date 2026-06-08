package andromeda.sei.ms.feature.azure.presentation.dto;

import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record UpdateWorkflowConfigRequest(
        @NotNull @Valid List<StatusMappingRequest> statusMappings,
        @NotNull @Valid List<TypeMappingRequest> typeMappings
) {
    public record StatusMappingRequest(
            @NotNull String rawStatus,
            @NotNull WorkItem.ResolvedStatus resolvedStatus
    ) {}

    public record TypeMappingRequest(
            @NotNull String rawType,
            @NotNull WorkItem.ResolvedType resolvedType
    ) {}
}
