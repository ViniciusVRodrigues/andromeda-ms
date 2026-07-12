package andromeda.sei.ms.feature.azure.infrastructure.persistence;

import andromeda.sei.ms.feature.azure.domain.AzureProjectBinding;
import andromeda.sei.ms.feature.azure.domain.FieldMapping;
import andromeda.sei.ms.feature.azure.domain.HierarchyLevel;
import andromeda.sei.ms.feature.azure.domain.StatusMapping;
import andromeda.sei.ms.feature.azure.domain.TypeMapping;
import andromeda.sei.ms.feature.azure.domain.WiqlFilter;
import andromeda.sei.ms.feature.azure.domain.WorkflowConfig;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AzureProjectBindingDocumentMapper {

    public AzureProjectBinding toDomain(AzureProjectBindingDocument doc) {
        return new AzureProjectBinding(
                doc.getId(),
                doc.getOrganizationId(),
                doc.getProjectId(),
                doc.getAzureProjectId(),
                doc.getAzureProjectName(),
                doc.isSyncEnabled(),
                doc.getLastSyncAt(),
                toWorkflowConfigDomain(doc.getWorkflowConfig()),
                doc.isActive(),
                doc.getCreatedAt(),
                doc.getUpdatedAt(),
                doc.getVersion()
        );
    }

    public AzureProjectBindingDocument toDocument(AzureProjectBinding binding) {
        return AzureProjectBindingDocument.builder()
                .id(binding.getId())
                .organizationId(binding.getOrganizationId())
                .projectId(binding.getProjectId())
                .azureProjectId(binding.getAzureProjectId())
                .azureProjectName(binding.getAzureProjectName())
                .syncEnabled(binding.isSyncEnabled())
                .lastSyncAt(binding.getLastSyncAt())
                .workflowConfig(toWorkflowConfigDocument(binding.getWorkflowConfig()))
                .active(binding.isActive())
                .createdAt(binding.getCreatedAt())
                .updatedAt(binding.getUpdatedAt())
                .version(binding.getVersion())
                .build();
    }

    private WorkflowConfig toWorkflowConfigDomain(AzureProjectBindingDocument.WorkflowConfigDocument doc) {
        if (doc == null) return null;
        List<StatusMapping> statusMappings = doc.getStatusMappings() == null ? List.of() :
                doc.getStatusMappings().stream()
                        .map(s -> new StatusMapping(s.getRawStatus(), s.getResolvedStatus()))
                        .toList();
        List<TypeMapping> typeMappings = doc.getTypeMappings() == null ? List.of() :
                doc.getTypeMappings().stream()
                        .map(t -> new TypeMapping(t.getRawType(), t.getResolvedType()))
                        .toList();
        List<FieldMapping> fieldMappings = doc.getFieldMappings() == null ? List.of() :
                doc.getFieldMappings().stream()
                        .map(f -> new FieldMapping(
                                FieldMapping.InternalFieldTarget.valueOf(f.getInternalField()),
                                f.getAzureFieldReferenceName(),
                                f.getAzureFieldType()))
                        .toList();
        WiqlFilter syncFilter = toWiqlFilterDomain(doc.getSyncFilter());
        WorkflowConfig.ConfigSource configSource = doc.getConfigSource() != null
                ? WorkflowConfig.ConfigSource.valueOf(doc.getConfigSource())
                : WorkflowConfig.ConfigSource.DEFAULT;
        List<HierarchyLevel> hierarchyLevels = doc.getHierarchyLevels() == null ? List.of() :
                doc.getHierarchyLevels().stream()
                        .map(h -> new HierarchyLevel(h.getRawType(), h.getRank()))
                        .toList();
        return new WorkflowConfig(statusMappings, typeMappings, fieldMappings, syncFilter, configSource, hierarchyLevels);
    }

    private AzureProjectBindingDocument.WorkflowConfigDocument toWorkflowConfigDocument(WorkflowConfig config) {
        if (config == null) return null;
        List<AzureProjectBindingDocument.StatusMappingDocument> statusMappings = config.getStatusMappings() == null ? List.of() :
                config.getStatusMappings().stream()
                        .map(s -> new AzureProjectBindingDocument.StatusMappingDocument(s.getRawStatus(), s.getResolvedStatus()))
                        .toList();
        List<AzureProjectBindingDocument.TypeMappingDocument> typeMappings = config.getTypeMappings() == null ? List.of() :
                config.getTypeMappings().stream()
                        .map(t -> new AzureProjectBindingDocument.TypeMappingDocument(t.getRawType(), t.getResolvedType()))
                        .toList();
        List<AzureProjectBindingDocument.FieldMappingDocument> fieldMappings = config.getFieldMappings() == null ? List.of() :
                config.getFieldMappings().stream()
                        .map(f -> new AzureProjectBindingDocument.FieldMappingDocument(
                                f.getInternalField().name(), f.getAzureFieldReferenceName(), f.getAzureFieldType()))
                        .toList();
        AzureProjectBindingDocument.WiqlFilterDocument syncFilter = toWiqlFilterDocument(config.getSyncFilter());
        List<AzureProjectBindingDocument.HierarchyLevelDocument> hierarchyLevels = config.getHierarchyLevels() == null ? List.of() :
                config.getHierarchyLevels().stream()
                        .map(h -> new AzureProjectBindingDocument.HierarchyLevelDocument(h.getRawType(), h.getRank()))
                        .toList();
        return new AzureProjectBindingDocument.WorkflowConfigDocument(
                statusMappings, typeMappings, fieldMappings, syncFilter, config.getConfigSource().name(), hierarchyLevels);
    }

    private WiqlFilter toWiqlFilterDomain(AzureProjectBindingDocument.WiqlFilterDocument doc) {
        if (doc == null) return null;
        return new WiqlFilter(
                doc.getWorkItemTypes() == null ? List.of() : doc.getWorkItemTypes(),
                doc.getAreaPath(),
                doc.getIterationPath());
    }

    private AzureProjectBindingDocument.WiqlFilterDocument toWiqlFilterDocument(WiqlFilter filter) {
        if (filter == null) return null;
        return new AzureProjectBindingDocument.WiqlFilterDocument(
                filter.getWorkItemTypes() == null ? List.of() : filter.getWorkItemTypes(),
                filter.getAreaPath(),
                filter.getIterationPath());
    }
}
