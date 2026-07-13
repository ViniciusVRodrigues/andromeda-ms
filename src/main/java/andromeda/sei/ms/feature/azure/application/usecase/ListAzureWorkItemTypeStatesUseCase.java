package andromeda.sei.ms.feature.azure.application.usecase;

import andromeda.sei.ms.feature.azure.application.dto.AzureWorkItemTypeStateOutput;
import andromeda.sei.ms.feature.azure.domain.AzureConnection;
import andromeda.sei.ms.feature.azure.domain.AzureProjectBinding;
import andromeda.sei.ms.feature.azure.domain.AzureConnectionRepository;
import andromeda.sei.ms.feature.azure.domain.AzureProjectBindingRepository;
import andromeda.sei.ms.feature.azure.domain.exception.AzureBindingNotFoundException;
import andromeda.sei.ms.feature.azure.domain.exception.AzureConnectionNotFoundException;
import andromeda.sei.ms.feature.azure.domain.exception.AzureIntrospectionFailedException;
import andromeda.sei.ms.feature.azure.domain.exception.AzureWorkItemTypeNotFoundException;
import andromeda.sei.ms.feature.azure.infrastructure.client.AzureAuthStrategyResolver;
import andromeda.sei.ms.feature.azure.infrastructure.client.AzureDevOpsClient;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ListAzureWorkItemTypeStatesUseCase {

    private final AzureProjectBindingRepository azureProjectBindingRepository;
    private final AzureConnectionRepository azureConnectionRepository;
    private final AzureAuthStrategyResolver authStrategyResolver;
    private final AzureDevOpsClient azureDevOpsClient;

    public List<AzureWorkItemTypeStateOutput> execute(String bindingId, String typeName) {
        String orgId = TenantContextHolder.get();

        AzureProjectBinding binding = azureProjectBindingRepository
                .findByIdAndOrganizationIdAndActiveTrue(bindingId, orgId)
                .orElseThrow(() -> new AzureBindingNotFoundException(bindingId));

        AzureConnection connection = azureConnectionRepository
                .findByOrganizationIdAndActiveTrue(orgId)
                .orElseThrow(() -> new AzureConnectionNotFoundException(orgId));

        String authHeader = authStrategyResolver.resolve(connection);

        try {
            return azureDevOpsClient
                    .getWorkItemTypeStates(connection.getAzureOrgUrl(), binding.getAzureProjectId(), typeName, authHeader)
                    .stream()
                    .map(s -> new AzureWorkItemTypeStateOutput(s.name(), s.color(), s.category()))
                    .toList();
        } catch (WebClientResponseException ex) {
            if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new AzureWorkItemTypeNotFoundException(typeName);
            }
            throw new AzureIntrospectionFailedException(
                    "Failed to fetch states for work item type " + typeName + ": " + ex.getMessage());
        }
    }
}
