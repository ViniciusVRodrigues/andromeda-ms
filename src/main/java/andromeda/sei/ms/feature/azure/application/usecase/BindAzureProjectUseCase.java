package andromeda.sei.ms.feature.azure.application.usecase;

import andromeda.sei.ms.feature.azure.application.DefaultWorkflowConfigFactory;
import andromeda.sei.ms.feature.azure.application.dto.BindAzureProjectInput;
import andromeda.sei.ms.feature.azure.domain.AzureConnection;
import andromeda.sei.ms.feature.azure.domain.AzureConnectionRepository;
import andromeda.sei.ms.feature.azure.domain.AzureProjectBinding;
import andromeda.sei.ms.feature.azure.domain.AzureProjectBindingRepository;
import andromeda.sei.ms.feature.azure.domain.WorkflowConfig;
import andromeda.sei.ms.feature.azure.domain.AzureAuthMethod;
import andromeda.sei.ms.feature.azure.infrastructure.client.AzureAuthStrategyResolver;
import andromeda.sei.ms.feature.azure.infrastructure.client.AzureDevOpsClient;
import andromeda.sei.ms.feature.azure.infrastructure.security.AzureTokenRefreshService;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class BindAzureProjectUseCase {

    private final AzureProjectBindingRepository azureProjectBindingRepository;
    private final AzureConnectionRepository azureConnectionRepository;
    private final AzureDevOpsClient azureDevOpsClient;
    private final AzureTokenRefreshService azureTokenRefreshService;
    private final AzureAuthStrategyResolver authStrategyResolver;

    public AzureProjectBinding execute(String projectId, BindAzureProjectInput input) {
        String orgId = TenantContextHolder.get();
        Instant now = Instant.now();

        AzureProjectBinding binding = azureProjectBindingRepository
                .findByOrganizationIdAndProjectIdAndActiveTrue(orgId, projectId)
                .orElseGet(() -> new AzureProjectBinding(
                        null, orgId, projectId, null, null, true, null, null, true, now, now, null));

        binding.setAzureProjectId(input.azureProjectId());
        binding.setAzureProjectName(input.azureProjectName());
        binding.setSyncEnabled(true);

        WorkflowConfig workflowConfig = detectAndSeedWorkflowConfig(orgId, input.azureProjectId());
        binding.setWorkflowConfig(workflowConfig);

        return azureProjectBindingRepository.save(binding);
    }

    private WorkflowConfig detectAndSeedWorkflowConfig(String orgId, String azureProjectId) {
        try {
            AzureConnection connection = azureConnectionRepository
                    .findByOrganizationIdAndActiveTrue(orgId).orElse(null);
            if (connection == null) return DefaultWorkflowConfigFactory.fallback();

            AzureConnection refreshed = connection.getAuthMethod() == AzureAuthMethod.PAT
                    ? connection
                    : azureTokenRefreshService.refreshIfNeeded(connection);
            String authHeader = authStrategyResolver.resolve(refreshed);
            var capabilities = azureDevOpsClient.getProjectCapabilities(
                    refreshed.getAzureOrgUrl(), azureProjectId, authHeader);
            String templateName = capabilities != null ? capabilities.templateName() : null;
            return DefaultWorkflowConfigFactory.forTemplateName(templateName);
        } catch (Exception e) {
            log.warn("Failed to detect ADO process template for project={}, using Scrum defaults: {}",
                    azureProjectId, e.getMessage());
            return DefaultWorkflowConfigFactory.fallback();
        }
    }
}
