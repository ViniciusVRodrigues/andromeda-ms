package andromeda.sei.ms.feature.azure.application.usecase;

import andromeda.sei.ms.feature.azure.domain.AzureConnection;
import andromeda.sei.ms.feature.azure.domain.AzureConnectionRepository;
import andromeda.sei.ms.feature.azure.domain.AzureProjectBinding;
import andromeda.sei.ms.feature.azure.domain.AzureProjectBindingRepository;
import andromeda.sei.ms.feature.azure.domain.exception.AzureConnectionNotFoundException;
import andromeda.sei.ms.feature.azure.infrastructure.security.AzureTokenRefreshService;
import andromeda.sei.ms.feature.integration.domain.SyncTrigger;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class TriggerAzureSyncUseCase {

    private final AzureConnectionRepository azureConnectionRepository;
    private final AzureProjectBindingRepository azureProjectBindingRepository;
    private final AzureTokenRefreshService azureTokenRefreshService;
    private final SyncAzureSprintsUseCase syncAzureSprintsUseCase;
    private final SyncAzureWorkItemsUseCase syncAzureWorkItemsUseCase;

    public void execute() {
        String orgId = TenantContextHolder.get();

        AzureConnection connection = azureConnectionRepository
                .findByOrganizationIdAndActiveTrue(orgId)
                .orElseThrow(() -> new AzureConnectionNotFoundException(orgId));

        AzureConnection refreshed = azureTokenRefreshService.refreshIfNeeded(connection);

        List<AzureProjectBinding> bindings = azureProjectBindingRepository
                .findAllByOrganizationIdAndSyncEnabledTrueAndActiveTrue(orgId);

        for (AzureProjectBinding binding : bindings) {
            try {
                syncAzureSprintsUseCase.execute(refreshed, binding, SyncTrigger.MANUAL);
                syncAzureWorkItemsUseCase.execute(refreshed, binding, SyncTrigger.MANUAL);
            } catch (Exception e) {
                log.error("Manual sync failed for org={} project={}", orgId, binding.getProjectId(), e);
            }
        }
    }
}
