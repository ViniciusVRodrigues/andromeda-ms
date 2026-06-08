package andromeda.sei.ms.feature.azure.application.usecase;

import andromeda.sei.ms.feature.azure.application.dto.AzureProjectOutput;
import andromeda.sei.ms.feature.azure.domain.AzureConnection;
import andromeda.sei.ms.feature.azure.domain.AzureConnectionRepository;
import andromeda.sei.ms.feature.azure.domain.exception.AzureConnectionNotFoundException;
import andromeda.sei.ms.feature.azure.infrastructure.client.AzureDevOpsClient;
import andromeda.sei.ms.shared.infrastructure.security.EncryptionService;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ListAzureProjectsUseCase {

    private final AzureConnectionRepository azureConnectionRepository;
    private final AzureDevOpsClient azureDevOpsClient;
    private final EncryptionService encryptionService;

    public List<AzureProjectOutput> execute() {
        String orgId = TenantContextHolder.get();
        AzureConnection connection = azureConnectionRepository
                .findByOrganizationIdAndActiveTrue(orgId)
                .orElseThrow(() -> new AzureConnectionNotFoundException(orgId));

        String accessToken = encryptionService.decrypt(connection.getAccessToken());
        return azureDevOpsClient.getProjects(connection.getAzureOrgUrl(), accessToken)
                .stream()
                .map(p -> new AzureProjectOutput(p.id(), p.name()))
                .toList();
    }
}
