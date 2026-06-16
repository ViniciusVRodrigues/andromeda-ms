package andromeda.sei.ms.feature.azure.infrastructure.scheduler;

import andromeda.sei.ms.feature.azure.application.usecase.SyncAzureSprintsUseCase;
import andromeda.sei.ms.feature.azure.application.usecase.SyncAzureWorkItemsUseCase;
import andromeda.sei.ms.feature.workitem.application.usecase.CalculateSprintMetricsUseCase;
import andromeda.sei.ms.feature.workitem.application.usecase.CalculateTokensPerSpUseCase;
import andromeda.sei.ms.feature.workitem.application.usecase.CalculateWorkItemMetricsUseCase;
import andromeda.sei.ms.feature.azure.domain.AzureConnection;
import andromeda.sei.ms.feature.azure.domain.AzureConnectionRepository;
import andromeda.sei.ms.feature.azure.domain.AzureConnectionStatus;
import andromeda.sei.ms.feature.azure.domain.AzureProjectBinding;
import andromeda.sei.ms.feature.azure.domain.AzureProjectBindingRepository;
import andromeda.sei.ms.feature.azure.infrastructure.security.AzureTokenRefreshService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AzureSyncJobTest {

    @Mock private AzureConnectionRepository azureConnectionRepository;
    @Mock private AzureProjectBindingRepository azureProjectBindingRepository;
    @Mock private AzureTokenRefreshService azureTokenRefreshService;
    @Mock private SyncAzureSprintsUseCase syncAzureSprintsUseCase;
    @Mock private SyncAzureWorkItemsUseCase syncAzureWorkItemsUseCase;
    @Mock private CalculateWorkItemMetricsUseCase calculateWorkItemMetricsUseCase;
    @Mock private CalculateSprintMetricsUseCase calculateSprintMetricsUseCase;
    @Mock private CalculateTokensPerSpUseCase calculateTokensPerSpUseCase;

    private AzureSyncJob job;

    @BeforeEach
    void setUp() {
        job = new AzureSyncJob(azureConnectionRepository, azureProjectBindingRepository,
                azureTokenRefreshService, syncAzureSprintsUseCase, syncAzureWorkItemsUseCase,
                calculateWorkItemMetricsUseCase, calculateSprintMetricsUseCase,
                calculateTokensPerSpUseCase);
    }

    @Test
    void shouldSkipExpiredConnections() {
        when(azureConnectionRepository.findAllByStatusAndActiveTrue(AzureConnectionStatus.CONNECTED))
                .thenReturn(List.of());

        job.run();

        verify(azureTokenRefreshService, never()).refreshIfNeeded(any());
    }

    @Test
    void shouldIncrementConsecutiveFailuresOnSyncFailure() {
        var connection = connectedConnection("org-1");
        var binding = binding("org-1", "proj-1");

        when(azureConnectionRepository.findAllByStatusAndActiveTrue(AzureConnectionStatus.CONNECTED))
                .thenReturn(List.of(connection));
        when(azureTokenRefreshService.refreshIfNeeded(connection)).thenReturn(connection);
        when(azureProjectBindingRepository.findAllByOrganizationIdAndSyncEnabledTrueAndActiveTrue("org-1"))
                .thenReturn(List.of(binding));
        doThrow(new RuntimeException("ADO error")).when(syncAzureSprintsUseCase).execute(any(), any(), any());
        when(azureConnectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        job.run();

        assertThat(connection.getConsecutiveFailures()).isEqualTo(1);
    }

    @Test
    void shouldResetConsecutiveFailuresOnSuccess() {
        var connection = connectedConnection("org-1");
        connection.setConsecutiveFailures(2);
        var binding = binding("org-1", "proj-1");

        when(azureConnectionRepository.findAllByStatusAndActiveTrue(AzureConnectionStatus.CONNECTED))
                .thenReturn(List.of(connection));
        when(azureTokenRefreshService.refreshIfNeeded(connection)).thenReturn(connection);
        when(azureProjectBindingRepository.findAllByOrganizationIdAndSyncEnabledTrueAndActiveTrue("org-1"))
                .thenReturn(List.of(binding));
        when(azureConnectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        job.run();

        assertThat(connection.getConsecutiveFailures()).isEqualTo(0);
        assertThat(connection.getLastSuccessAt()).isNotNull();
    }

    @Test
    void shouldSetErrorStatusAfterThreeConsecutiveFailures() {
        var connection = connectedConnection("org-1");
        connection.setConsecutiveFailures(2);

        when(azureConnectionRepository.findAllByStatusAndActiveTrue(AzureConnectionStatus.CONNECTED))
                .thenReturn(List.of(connection));
        when(azureTokenRefreshService.refreshIfNeeded(connection))
                .thenThrow(new RuntimeException("token refresh failed"));
        when(azureConnectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        job.run();

        assertThat(connection.getConsecutiveFailures()).isEqualTo(3);
        assertThat(connection.getStatus()).isEqualTo(AzureConnectionStatus.ERROR);
    }

    private AzureConnection connectedConnection(String orgId) {
        return new AzureConnection("conn-1", orgId, "https://dev.azure.com/myorg",
                "enc-access", "enc-refresh", Instant.now().plusSeconds(3600),
                "user-1", AzureConnectionStatus.CONNECTED,
                null, 0, null, null, true, Instant.now(), Instant.now(), null,
                andromeda.sei.ms.feature.azure.domain.AzureAuthMethod.OAUTH, null);
    }

    @Test
    void shouldCallMetricUseCasesAfterSuccessfulWorkItemSync() {
        var connection = connectedConnection("org-1");
        var binding = binding("org-1", "proj-1");

        when(azureConnectionRepository.findAllByStatusAndActiveTrue(AzureConnectionStatus.CONNECTED))
                .thenReturn(List.of(connection));
        when(azureTokenRefreshService.refreshIfNeeded(connection)).thenReturn(connection);
        when(azureProjectBindingRepository.findAllByOrganizationIdAndSyncEnabledTrueAndActiveTrue("org-1"))
                .thenReturn(List.of(binding));
        when(azureConnectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        job.run();

        verify(calculateWorkItemMetricsUseCase).execute(eq("org-1"), eq("proj-1"));
        verify(calculateSprintMetricsUseCase).execute(eq("org-1"), eq("proj-1"));
    }

    @Test
    void shouldContinueWhenWorkItemMetricsUseCaseThrows() {
        var connection = connectedConnection("org-1");
        var binding = binding("org-1", "proj-1");

        when(azureConnectionRepository.findAllByStatusAndActiveTrue(AzureConnectionStatus.CONNECTED))
                .thenReturn(List.of(connection));
        when(azureTokenRefreshService.refreshIfNeeded(connection)).thenReturn(connection);
        when(azureProjectBindingRepository.findAllByOrganizationIdAndSyncEnabledTrueAndActiveTrue("org-1"))
                .thenReturn(List.of(binding));
        doThrow(new RuntimeException("metrics error")).when(calculateWorkItemMetricsUseCase).execute(any(), any());
        when(azureConnectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        job.run();

        verify(calculateSprintMetricsUseCase).execute(eq("org-1"), eq("proj-1"));
        assertThat(connection.getConsecutiveFailures()).isEqualTo(0);
    }

    @Test
    void shouldNotCallMetricUseCasesWhenSyncFails() {
        var connection = connectedConnection("org-1");
        var binding = binding("org-1", "proj-1");

        when(azureConnectionRepository.findAllByStatusAndActiveTrue(AzureConnectionStatus.CONNECTED))
                .thenReturn(List.of(connection));
        when(azureTokenRefreshService.refreshIfNeeded(connection)).thenReturn(connection);
        when(azureProjectBindingRepository.findAllByOrganizationIdAndSyncEnabledTrueAndActiveTrue("org-1"))
                .thenReturn(List.of(binding));
        doThrow(new RuntimeException("ADO error")).when(syncAzureWorkItemsUseCase).execute(any(), any(), any());
        when(azureConnectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        job.run();

        verify(calculateWorkItemMetricsUseCase, never()).execute(any(), any());
        verify(calculateSprintMetricsUseCase, never()).execute(any(), any());
    }

    private AzureProjectBinding binding(String orgId, String projectId) {
        return new AzureProjectBinding("bind-1", orgId, projectId, "azure-proj-1", "My Project",
                true, null, null, true, Instant.now(), Instant.now(), null);
    }
}
