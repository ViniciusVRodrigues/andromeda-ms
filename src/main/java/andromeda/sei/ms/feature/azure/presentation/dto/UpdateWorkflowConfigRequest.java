package andromeda.sei.ms.feature.azure.presentation.dto;

import andromeda.sei.ms.feature.azure.domain.FieldMapping;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record UpdateWorkflowConfigRequest(
        @NotNull @Valid List<StatusMappingRequest> statusMappings,
        @NotNull @Valid List<TypeMappingRequest> typeMappings,
        @Valid List<FieldMappingRequest> fieldMappings,
        @Valid WiqlFilterRequest syncFilter,
        @Valid List<HierarchyLevelRequest> hierarchyLevels
) {
    public record StatusMappingRequest(
            @NotNull String rawStatus,
            @NotNull WorkItem.ResolvedStatus resolvedStatus
    ) {}

    public record TypeMappingRequest(
            @NotNull String rawType,
            @NotNull WorkItem.ResolvedType resolvedType
    ) {}

    public record FieldMappingRequest(
            @NotNull FieldMapping.InternalFieldTarget internalField,
            @NotNull String azureFieldReferenceName,
            String azureFieldType
    ) {}

    public record WiqlFilterRequest(
            List<String> workItemTypes,
            String areaPath,
            String iterationPath
    ) {}

    public record HierarchyLevelRequest(
            @NotNull String rawType,
            @NotNull Integer rank
    ) {}
}
