package andromeda.sei.ms.feature.azure.application.usecase;

import andromeda.sei.ms.feature.azure.application.dto.UpdateAzureBindingInput;
import andromeda.sei.ms.feature.azure.domain.AzureProjectBinding;
import andromeda.sei.ms.feature.azure.domain.AzureProjectBindingRepository;
import andromeda.sei.ms.feature.azure.domain.exception.AzureBindingNotFoundException;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UpdateAzureProjectBindingSyncUseCase {

    private final AzureProjectBindingRepository azureProjectBindingRepository;

    public AzureProjectBinding execute(String projectId, UpdateAzureBindingInput input) {
        String orgId = TenantContextHolder.get();
        AzureProjectBinding binding = azureProjectBindingRepository
                .findByOrganizationIdAndProjectIdAndActiveTrue(orgId, projectId)
                .orElseThrow(() -> new AzureBindingNotFoundException(projectId));

        binding.setSyncEnabled(input.syncEnabled());
        return azureProjectBindingRepository.save(binding);
    }
}
