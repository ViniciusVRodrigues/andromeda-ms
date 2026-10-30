package andromeda.sei.ms.feature.azure.presentation.dto;

import andromeda.sei.ms.feature.azure.domain.FieldMapping;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;

import java.util.List;

public record WorkflowConfigResponse(
        List<StatusMappingResponse> statusMappings,
        List<TypeMappingResponse> typeMappings,
        List<FieldMappingResponse> fieldMappings,
        WiqlFilterResponse syncFilter,
        String configSource,
        List<HierarchyLevelResponse> hierarchyLevels
) {
    public record StatusMappingResponse(String workItemType, String rawStatus, WorkItem.ResolvedStatus resolvedStatus) {}
    public record TypeMappingResponse(String rawType, WorkItem.ResolvedType resolvedType, boolean receivesAi) {}
    public record FieldMappingResponse(FieldMapping.InternalFieldTarget internalField, String workItemType,
                                        String azureFieldReferenceName, String azureFieldType) {}
    public record WiqlFilterResponse(List<String> workItemTypes, List<String> excludedWorkItemTypes, String areaPath, String iterationPath) {}
    public record HierarchyLevelResponse(String rawType, Integer rank) {}
}
