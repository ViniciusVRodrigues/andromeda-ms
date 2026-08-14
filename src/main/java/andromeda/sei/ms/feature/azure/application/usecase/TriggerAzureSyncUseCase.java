package andromeda.sei.ms.feature.azure.application.usecase;

import andromeda.sei.ms.feature.azure.domain.AzureAuthMethod;
import andromeda.sei.ms.feature.azure.domain.AzureConnection;
import andromeda.sei.ms.feature.azure.domain.AzureConnectionRepository;
import andromeda.sei.ms.feature.azure.domain.AzureProjectBinding;
import andromeda.sei.ms.feature.azure.domain.AzureProjectBindingRepository;
import andromeda.sei.ms.feature.azure.domain.exception.AzureBindingNotFoundException;
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

    /**
     * Preserves today's behavior exactly: syncs every {@code syncEnabled && active} binding
     * in the organization.
     */
    public void execute() {
        execute(null);
    }

    /**
     * @param bindingId when present, scopes the sync to just that binding (regardless of its
     *                  {@code syncEnabled} value — the caller, e.g. wizard-completion, is
     *                  responsible for having already gated on that); when {@code null}, syncs
     *                  every {@code syncEnabled && active} binding in the org (today's behavior).
     */
    public void execute(String bindingId) {
        String orgId = TenantContextHolder.get();

        AzureConnection connection = azureConnectionRepository
                .findByOrganizationIdAndActiveTrue(orgId)
                .orElseThrow(() -> new AzureConnectionNotFoundException(orgId));

        AzureConnection refreshed = connection.getAuthMethod() == AzureAuthMethod.PAT
                ? connection
                : azureTokenRefreshService.refreshIfNeeded(connection);

        List<AzureProjectBinding> bindings = bindingId == null
                ? azureProjectBindingRepository.findAllByOrganizationIdAndSyncEnabledTrueAndActiveTrue(orgId)
                : List.of(azureProjectBindingRepository
                        .findByIdAndOrganizationIdAndActiveTrue(bindingId, orgId)
                        .orElseThrow(() -> new AzureBindingNotFoundException(bindingId)));

        for (AzureProjectBinding binding : bindings) {
            try {
                /* Ambos os syncs gravam lastSyncAt no mesmo binding. O segundo precisa
                 * partir da instância devolvida pelo primeiro — seguir com a original
                 * carregaria uma version já consumida e o save falharia por lock
                 * otimista, derrubando um sync que na verdade deu certo. */
                AzureProjectBinding afterSprints =
                        syncAzureSprintsUseCase.execute(refreshed, binding, SyncTrigger.MANUAL);
                syncAzureWorkItemsUseCase.execute(refreshed, afterSprints, SyncTrigger.MANUAL);
            } catch (Exception e) {
                log.error("Manual sync failed for org={} project={}", orgId, binding.getProjectId(), e);
            }
        }
    }
}
