package andromeda.sei.ms.feature.azure.application.usecase;

import andromeda.sei.ms.feature.azure.application.dto.UpdateWorkflowConfigInput;
import andromeda.sei.ms.feature.azure.domain.AzureProjectBinding;
import andromeda.sei.ms.feature.azure.domain.AzureProjectBindingRepository;
import andromeda.sei.ms.feature.azure.domain.StatusMapping;
import andromeda.sei.ms.feature.azure.domain.TypeMapping;
import andromeda.sei.ms.feature.azure.domain.WorkflowConfig;
import andromeda.sei.ms.feature.azure.domain.exception.AzureBindingNotFoundException;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

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

        binding.setWorkflowConfig(new WorkflowConfig(statusMappings, typeMappings, WorkflowConfig.ConfigSource.CUSTOM));

        return azureProjectBindingRepository.save(binding);
    }
}
