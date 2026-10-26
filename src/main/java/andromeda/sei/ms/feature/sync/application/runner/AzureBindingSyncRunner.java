package andromeda.sei.ms.feature.sync.application.runner;

import andromeda.sei.ms.feature.allocation.application.usecase.RefreshAllocationSuggestionsUseCase;
import andromeda.sei.ms.feature.azure.application.dto.ActivityDay;
import andromeda.sei.ms.feature.azure.application.dto.AzureWorkItemsSyncResult;
import andromeda.sei.ms.feature.azure.application.usecase.SyncAzureSprintsUseCase;
import andromeda.sei.ms.feature.azure.application.usecase.SyncAzureWorkItemsUseCase;
import andromeda.sei.ms.feature.azure.domain.AzureAuthMethod;
import andromeda.sei.ms.feature.azure.domain.AzureConnection;
import andromeda.sei.ms.feature.azure.domain.AzureConnectionRepository;
import andromeda.sei.ms.feature.azure.domain.AzureConnectionStatus;
import andromeda.sei.ms.feature.azure.domain.AzureProjectBinding;
import andromeda.sei.ms.feature.azure.domain.AzureProjectBindingRepository;
import andromeda.sei.ms.feature.azure.infrastructure.security.AzureTokenRefreshService;
import andromeda.sei.ms.feature.integration.domain.Integration;
import andromeda.sei.ms.feature.integration.domain.SyncStatus;
import andromeda.sei.ms.feature.integration.domain.SyncTrigger;
import andromeda.sei.ms.feature.sync.application.ConnectionHealthUpdater;
import andromeda.sei.ms.feature.sync.domain.BindingSyncOutcome;
import andromeda.sei.ms.feature.sync.domain.SyncTarget;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Sync de bindings do Azure DevOps: sprints e depois work items, por binding, isolando falhas.
 * Logica que antes vivia em {@code AzureSyncJob} e {@code TriggerAzureSyncUseCase}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AzureBindingSyncRunner implements BindingSyncRunner {

    static final String CONNECTION_NOT_FOUND = "AZURE_CONNECTION_NOT_FOUND";
    static final String CONNECTION_IN_ERROR = "AZURE_CONNECTION_ERROR";
    static final String TOKEN_REFRESH_FAILED = "AZURE_TOKEN_REFRESH_FAILED";
    static final String BINDING_NOT_FOUND = "AZURE_BINDING_NOT_FOUND";
    static final String SYNC_FAILED = "AZURE_SYNC_FAILED";
    static final String WORK_ITEMS_PARTIAL = "AZURE_SYNC_WORK_ITEMS_PARTIAL";

    private final AzureConnectionRepository azureConnectionRepository;
    private final AzureProjectBindingRepository azureProjectBindingRepository;
    private final AzureTokenRefreshService azureTokenRefreshService;
    private final SyncAzureSprintsUseCase syncAzureSprintsUseCase;
    private final SyncAzureWorkItemsUseCase syncAzureWorkItemsUseCase;
    private final ConnectionHealthUpdater connectionHealthUpdater;
    private final RefreshAllocationSuggestionsUseCase refreshAllocationSuggestionsUseCase;

    @Override
    public Integration integration() {
        return Integration.AZURE_DEVOPS;
    }

    @Override
    public void run(BindingSyncBatch batch, Consumer<BindingSyncOutcome> onOutcome) {
        String orgId = batch.organizationId();
        Optional<AzureConnection> found = azureConnectionRepository.findByOrganizationIdAndActiveTrue(orgId);
        if (found.isEmpty()) {
            failAll(batch, onOutcome, CONNECTION_NOT_FOUND, "No active Azure DevOps connection");
            return;
        }
        AzureConnection connection = found.get();
        /* O cron pula conexoes em ERROR; o refresh manual nao — e o caminho de recuperacao (D3). */
        if (batch.trigger() == SyncTrigger.SCHEDULED && connection.getStatus() == AzureConnectionStatus.ERROR) {
            failAll(batch, onOutcome, CONNECTION_IN_ERROR, "Azure DevOps connection is in ERROR");
            return;
        }

        AzureConnection ready;
        try {
            ready = connection.getAuthMethod() == AzureAuthMethod.PAT
                    ? connection
                    : azureTokenRefreshService.refreshIfNeeded(connection);
        } catch (Exception e) {
            log.error("Azure token refresh failed for org={}", orgId, e);
            failAll(batch, onOutcome, TOKEN_REFRESH_FAILED, SyncFailures.messageOf(e));
            connectionHealthUpdater.recordAzure(orgId, false);
            return;
        }

        boolean anyFailed = false;
        Set<ActivityDay> newActivityDays = new HashSet<>();
        for (SyncTarget target : batch.targets()) {
            BindingSyncOutcome outcome = syncBinding(ready, target, batch, newActivityDays);
            anyFailed |= !outcome.reachedProvider();
            onOutcome.accept(outcome);
        }
        connectionHealthUpdater.recordAzure(orgId, !anyFailed);
        refreshSuggestions(orgId, newActivityDays);
    }

    /** One refresh for the union of every binding's new activity days; never breaks the sync. */
    private void refreshSuggestions(String orgId, Set<ActivityDay> newActivityDays) {
        if (newActivityDays.isEmpty()) return;
        try {
            refreshAllocationSuggestionsUseCase.execute(orgId, newActivityDays);
        } catch (Exception e) {
            log.warn("Allocation suggestion refresh failed after Azure sync for org={}", orgId, e);
        }
    }

    private BindingSyncOutcome syncBinding(AzureConnection connection, SyncTarget target, BindingSyncBatch batch,
                                           Set<ActivityDay> newActivityDays) {
        String orgId = batch.organizationId();
        try {
            Optional<AzureProjectBinding> found =
                    azureProjectBindingRepository.findByIdAndOrganizationIdAndActiveTrue(target.bindingId(), orgId);
            if (found.isEmpty()) {
                return BindingSyncOutcome.failed(target, BINDING_NOT_FOUND, "Binding not found: " + target.bindingId());
            }
            AzureProjectBinding binding = found.get();
            if (batch.resetWatermarks()) {
                binding.setWorkItemsWatermark(null);
                binding = azureProjectBindingRepository.save(binding);
            }
            /* Ambos os syncs gravam no mesmo binding: o de work items precisa partir da instancia
             * devolvida pelo de sprints, senao carregaria uma version ja consumida (lock otimista). */
            AzureProjectBinding afterSprints = syncAzureSprintsUseCase.execute(connection, binding, batch.trigger());
            AzureWorkItemsSyncResult result = syncAzureWorkItemsUseCase.sync(
                    connection, afterSprints, batch.trigger(), batch.backfillFrom());
            newActivityDays.addAll(result.newActivityDays());
            return toOutcome(target, result);
        } catch (Exception e) {
            log.error("Azure sync failed for org={} project={} binding={}", orgId, target.projectId(), target.bindingId(), e);
            return BindingSyncOutcome.failed(target, SyncFailures.codeOf(e, SYNC_FAILED), SyncFailures.messageOf(e));
        }
    }

    private static BindingSyncOutcome toOutcome(SyncTarget target, AzureWorkItemsSyncResult result) {
        if (result.status() == SyncStatus.SUCCESS) {
            return BindingSyncOutcome.success(target, result.processed());
        }
        if (result.status() == SyncStatus.PARTIAL) {
            return BindingSyncOutcome.partial(target, WORK_ITEMS_PARTIAL, result.processed(), result.failed());
        }
        return new BindingSyncOutcome(target, SyncStatus.FAILED, SYNC_FAILED,
                "Every work item failed to sync", result.processed(), result.failed());
    }

    private static void failAll(BindingSyncBatch batch, Consumer<BindingSyncOutcome> onOutcome,
                                String errorCode, String message) {
        batch.targets().forEach(target -> onOutcome.accept(BindingSyncOutcome.failed(target, errorCode, message)));
    }
}
