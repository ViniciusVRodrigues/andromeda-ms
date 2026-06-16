package andromeda.sei.ms.feature.azure.infrastructure.scheduler;

import andromeda.sei.ms.feature.azure.application.usecase.SyncAzureSprintsUseCase;
import andromeda.sei.ms.feature.azure.application.usecase.SyncAzureWorkItemsUseCase;
import andromeda.sei.ms.feature.workitem.application.usecase.CalculateSprintMetricsUseCase;
import andromeda.sei.ms.feature.workitem.application.usecase.CalculateTokensPerSpUseCase;
import andromeda.sei.ms.feature.workitem.application.usecase.CalculateWorkItemMetricsUseCase;
import andromeda.sei.ms.feature.azure.domain.AzureAuthMethod;
import andromeda.sei.ms.feature.azure.domain.AzureConnection;
import andromeda.sei.ms.feature.azure.domain.AzureConnectionRepository;
import andromeda.sei.ms.feature.azure.domain.AzureConnectionStatus;
import andromeda.sei.ms.feature.azure.domain.AzureProjectBinding;
import andromeda.sei.ms.feature.azure.domain.AzureProjectBindingRepository;
import andromeda.sei.ms.feature.azure.infrastructure.security.AzureTokenRefreshService;
import andromeda.sei.ms.feature.integration.domain.SyncTrigger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AzureSyncJob {

    private final AzureConnectionRepository azureConnectionRepository;
    private final AzureProjectBindingRepository azureProjectBindingRepository;
    private final AzureTokenRefreshService azureTokenRefreshService;
    private final SyncAzureSprintsUseCase syncAzureSprintsUseCase;
    private final SyncAzureWorkItemsUseCase syncAzureWorkItemsUseCase;
    private final CalculateWorkItemMetricsUseCase calculateWorkItemMetricsUseCase;
    private final CalculateSprintMetricsUseCase calculateSprintMetricsUseCase;
    private final CalculateTokensPerSpUseCase calculateTokensPerSpUseCase;

    @Scheduled(cron = "0 */30 * * * *")
    public void run() {
        List<AzureConnection> connections = azureConnectionRepository
                .findAllByStatusAndActiveTrue(AzureConnectionStatus.CONNECTED);

        for (AzureConnection connection : connections) {
            syncOrganization(connection);
        }
    }

    private void syncOrganization(AzureConnection connection) {
        AzureConnection refreshed;
        if (connection.getAuthMethod() == AzureAuthMethod.PAT) {
            refreshed = connection;
        } else {
            try {
                refreshed = azureTokenRefreshService.refreshIfNeeded(connection);
            } catch (Exception e) {
                log.error("Token refresh failed for org={}", connection.getOrganizationId(), e);
                incrementFailures(connection);
                return;
            }
        }

        List<AzureProjectBinding> bindings = azureProjectBindingRepository
                .findAllByOrganizationIdAndSyncEnabledTrueAndActiveTrue(refreshed.getOrganizationId());

        boolean anyFailure = false;
        for (AzureProjectBinding binding : bindings) {
            String orgId = refreshed.getOrganizationId();
            String projectId = binding.getProjectId();
            try {
                syncAzureSprintsUseCase.execute(refreshed, binding, SyncTrigger.SCHEDULED);
                syncAzureWorkItemsUseCase.execute(refreshed, binding, SyncTrigger.SCHEDULED);
            } catch (WebClientResponseException e) {
                log.error("Sync failed for org={} project={} httpStatus={} azureError={}",
                        orgId, projectId, e.getStatusCode().value(), e.getResponseBodyAsString(), e);
                anyFailure = true;
                continue;
            } catch (Exception e) {
                log.error("Sync failed for org={} project={}", orgId, projectId, e);
                anyFailure = true;
                continue;
            }
            try {
                calculateWorkItemMetricsUseCase.execute(orgId, projectId);
            } catch (Exception e) {
                log.error("Work item metrics calculation failed for org={} project={}", orgId, projectId, e);
            }
            try {
                calculateSprintMetricsUseCase.execute(orgId, projectId);
            } catch (Exception e) {
                log.error("Sprint metrics calculation failed for org={} project={}", orgId, projectId, e);
            }
            try {
                calculateTokensPerSpUseCase.execute(orgId, projectId);
            } catch (Exception e) {
                log.error("Tokens per SP calculation failed for org={} project={}", orgId, projectId, e);
            }
        }

        if (anyFailure) {
            incrementFailures(refreshed);
        } else {
            resetFailures(refreshed);
        }
    }

    private void incrementFailures(AzureConnection connection) {
        connection.setConsecutiveFailures(connection.getConsecutiveFailures() + 1);
        if (connection.getConsecutiveFailures() >= 3) {
            connection.setStatus(AzureConnectionStatus.ERROR);
        }
        azureConnectionRepository.save(connection);
    }

    private void resetFailures(AzureConnection connection) {
        connection.setConsecutiveFailures(0);
        connection.setLastSuccessAt(Instant.now());
        if (connection.getStatus() == AzureConnectionStatus.ERROR) {
            connection.setStatus(AzureConnectionStatus.CONNECTED);
        }
        azureConnectionRepository.save(connection);
    }
}
