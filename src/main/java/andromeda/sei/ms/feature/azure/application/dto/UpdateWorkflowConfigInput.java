package andromeda.sei.ms.feature.azure.application.dto;

import andromeda.sei.ms.feature.azure.domain.FieldMapping;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;

import java.util.List;

public record UpdateWorkflowConfigInput(
        List<StatusMappingInput> statusMappings,
        List<TypeMappingInput> typeMappings,
        List<FieldMappingInput> fieldMappings,
        WiqlFilterInput syncFilter,
        List<HierarchyLevelInput> hierarchyLevels
) {
    public record StatusMappingInput(String workItemType, String rawStatus, WorkItem.ResolvedStatus resolvedStatus) {}
    public record TypeMappingInput(String rawType, WorkItem.ResolvedType resolvedType, Boolean receivesAi) {
        public TypeMappingInput(String rawType, WorkItem.ResolvedType resolvedType) {
            this(rawType, resolvedType, null);
        }
    }
    public record FieldMappingInput(FieldMapping.InternalFieldTarget internalField, String workItemType,
                                     String azureFieldReferenceName, String azureFieldType) {}
    public record WiqlFilterInput(List<String> workItemTypes, List<String> excludedWorkItemTypes, String areaPath, String iterationPath) {}
    public record HierarchyLevelInput(String rawType, Integer rank) {}
}
