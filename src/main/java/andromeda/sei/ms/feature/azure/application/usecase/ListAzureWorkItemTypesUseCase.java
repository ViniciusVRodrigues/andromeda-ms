package andromeda.sei.ms.feature.azure.application.usecase;

import andromeda.sei.ms.feature.azure.application.dto.AzureWorkItemTypeOutput;
import andromeda.sei.ms.feature.azure.domain.AzureConnection;
import andromeda.sei.ms.feature.azure.domain.AzureConnectionRepository;
import andromeda.sei.ms.feature.azure.domain.AzureProjectBinding;
import andromeda.sei.ms.feature.azure.domain.AzureProjectBindingRepository;
import andromeda.sei.ms.feature.azure.domain.exception.AzureBindingNotFoundException;
import andromeda.sei.ms.feature.azure.domain.exception.AzureConnectionNotFoundException;
import andromeda.sei.ms.feature.azure.infrastructure.client.AzureAuthStrategyResolver;
import andromeda.sei.ms.feature.azure.infrastructure.client.AzureDevOpsClient;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ListAzureWorkItemTypesUseCase {

    private final AzureProjectBindingRepository azureProjectBindingRepository;
    private final AzureConnectionRepository azureConnectionRepository;
    private final AzureAuthStrategyResolver authStrategyResolver;
    private final AzureDevOpsClient azureDevOpsClient;

    public List<AzureWorkItemTypeOutput> execute(String bindingId) {
        String orgId = TenantContextHolder.get();

        AzureProjectBinding binding = azureProjectBindingRepository
                .findByIdAndOrganizationIdAndActiveTrue(bindingId, orgId)
                .orElseThrow(() -> new AzureBindingNotFoundException(bindingId));

        AzureConnection connection = azureConnectionRepository
                .findByOrganizationIdAndActiveTrue(orgId)
                .orElseThrow(() -> new AzureConnectionNotFoundException(orgId));

        String authHeader = authStrategyResolver.resolve(connection);

        return azureDevOpsClient
                .getWorkItemTypes(connection.getAzureOrgUrl(), binding.getAzureProjectId(), authHeader)
                .stream()
                .map(t -> new AzureWorkItemTypeOutput(t.name(), t.referenceName()))
                .toList();
    }
}
