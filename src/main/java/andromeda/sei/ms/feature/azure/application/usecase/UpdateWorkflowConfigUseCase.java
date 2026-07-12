package andromeda.sei.ms.feature.azure.application.usecase;

import andromeda.sei.ms.feature.azure.application.dto.UpdateWorkflowConfigInput;
import andromeda.sei.ms.feature.azure.domain.AzureProjectBinding;
import andromeda.sei.ms.feature.azure.domain.AzureProjectBindingRepository;
import andromeda.sei.ms.feature.azure.domain.FieldMapping;
import andromeda.sei.ms.feature.azure.domain.HierarchyLevel;
import andromeda.sei.ms.feature.azure.domain.StatusMapping;
import andromeda.sei.ms.feature.azure.domain.TypeMapping;
import andromeda.sei.ms.feature.azure.domain.WiqlFilter;
import andromeda.sei.ms.feature.azure.domain.WorkflowConfig;
import andromeda.sei.ms.feature.azure.domain.exception.AzureBindingNotFoundException;
import andromeda.sei.ms.feature.azure.domain.exception.AzureFieldMappingDuplicateTargetException;
import andromeda.sei.ms.feature.azure.domain.exception.AzureHierarchyLevelDuplicateTypeException;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class UpdateWorkflowConfigUseCase {

    private final AzureProjectBindingRepository azureProjectBindingRepository;

    public AzureProjectBinding execute(String bindingId, UpdateWorkflowConfigInput input) {
        String orgId = TenantContextHolder.get();

        AzureProjectBinding binding = azureProjectBindingRepository
                .findByIdAndOrganizationIdAndActiveTrue(bindingId, orgId)
                .orElseThrow(() -> new AzureBindingNotFoundException(bindingId));

        List<StatusMapping> statusMappings = input.statusMappings() == null ? List.of() :
                input.statusMappings().stream()
                        .map(s -> new StatusMapping(s.rawStatus(), s.resolvedStatus()))
                        .toList();

        List<TypeMapping> typeMappings = input.typeMappings() == null ? List.of() :
                input.typeMappings().stream()
                        .map(t -> new TypeMapping(t.rawType(), t.resolvedType()))
                        .toList();

        List<FieldMapping> fieldMappings = input.fieldMappings() == null ? List.of() :
                input.fieldMappings().stream()
                        .map(f -> new FieldMapping(f.internalField(), f.azureFieldReferenceName(), f.azureFieldType()))
                        .toList();
        validateNoDuplicateTargets(fieldMappings);

        WiqlFilter syncFilter = input.syncFilter() == null ? null :
                new WiqlFilter(
                        input.syncFilter().workItemTypes() == null ? List.of() : input.syncFilter().workItemTypes(),
                        input.syncFilter().areaPath(),
                        input.syncFilter().iterationPath());

        List<HierarchyLevel> hierarchyLevels = input.hierarchyLevels() == null ? List.of() :
                input.hierarchyLevels().stream()
                        .map(h -> new HierarchyLevel(h.rawType(), h.rank()))
                        .toList();
        validateNoDuplicateRawTypes(hierarchyLevels);

        binding.setWorkflowConfig(new WorkflowConfig(
                statusMappings, typeMappings, fieldMappings, syncFilter, WorkflowConfig.ConfigSource.CUSTOM,
                hierarchyLevels));

        return azureProjectBindingRepository.save(binding);
    }

    private void validateNoDuplicateTargets(List<FieldMapping> fieldMappings) {
        Set<FieldMapping.InternalFieldTarget> seen = new HashSet<>();
        for (FieldMapping mapping : fieldMappings) {
            if (!seen.add(mapping.getInternalField())) {
                throw new AzureFieldMappingDuplicateTargetException(mapping.getInternalField().name());
            }
        }
    }

    private void validateNoDuplicateRawTypes(List<HierarchyLevel> hierarchyLevels) {
        Set<String> seen = new HashSet<>();
        for (HierarchyLevel level : hierarchyLevels) {
            if (!seen.add(level.getRawType())) {
                throw new AzureHierarchyLevelDuplicateTypeException(level.getRawType());
            }
        }
    }
}
