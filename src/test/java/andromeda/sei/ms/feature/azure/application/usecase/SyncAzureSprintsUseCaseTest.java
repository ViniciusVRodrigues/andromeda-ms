package andromeda.sei.ms.feature.azure.application.usecase;

import andromeda.sei.ms.feature.azure.domain.AzureConnection;
import andromeda.sei.ms.feature.azure.domain.AzureConnectionStatus;
import andromeda.sei.ms.feature.azure.domain.AzureProjectBinding;
import andromeda.sei.ms.feature.azure.infrastructure.client.AdoIterationDto;
import andromeda.sei.ms.feature.azure.infrastructure.client.AdoTeamDto;
import andromeda.sei.ms.feature.azure.infrastructure.client.AzureDevOpsClient;
import andromeda.sei.ms.feature.integration.domain.IntegrationSyncLog;
import andromeda.sei.ms.feature.integration.domain.IntegrationSyncLogRepository;
import andromeda.sei.ms.feature.integration.domain.SyncStatus;
import andromeda.sei.ms.feature.integration.domain.SyncTrigger;
import andromeda.sei.ms.feature.workitem.domain.SprintRepository;
import andromeda.sei.ms.feature.azure.infrastructure.client.AzureAuthStrategyResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SyncAzureSprintsUseCaseTest {

    @Mock private IntegrationSyncLogRepository syncLogRepository;
    @Mock private AzureAuthStrategyResolver authStrategyResolver;
    @Mock private SprintRepository sprintRepository;
    @Mock private AzureDevOpsClient azureDevOpsClient;

    @InjectMocks
    private SyncAzureSprintsUseCase useCase;

    private AzureConnection connection;
    private AzureProjectBinding binding;

    @BeforeEach
    void setUp() {
        Instant now = Instant.now();
        connection = new AzureConnection("conn-1", "org-1", "https://dev.azure.com/org",
                "enc-access", "enc-refresh", now.plusSeconds(3600),
                "user-1", AzureConnectionStatus.CONNECTED, null, 0, null, null, true, now, now, null,
                andromeda.sei.ms.feature.azure.domain.AzureAuthMethod.OAUTH, null);

        binding = new AzureProjectBinding("bind-1", "org-1", "proj-1", "azure-proj-1", "My Project",
                true, null, null, true, now, now, null);

        IntegrationSyncLog syncLog = new IntegrationSyncLog(
                "log-1", "org-1", null, null, SyncStatus.RUNNING, null, "proj-1",
                now, null, null, 0, 0, null, null);
        when(syncLogRepository.save(any())).thenReturn(syncLog);
        when(authStrategyResolver.resolve(any())).thenReturn("Bearer plain-access");
    }

    @Test
    void shouldCallGetTeamsAndGetIterationsPerTeam() {
        when(azureDevOpsClient.getTeams(anyString(), anyString(), anyString()))
                .thenReturn(List.of(new AdoTeamDto("team-1", "Backend Team")));
        when(azureDevOpsClient.getIterations(anyString(), anyString(), eq("team-1"), anyString()))
                .thenReturn(List.of(iteration("iter-1")));
        when(sprintRepository.findByOrganizationIdAndExternalIdAndTeamId(anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(sprintRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(connection, binding, SyncTrigger.SCHEDULED);

        verify(azureDevOpsClient).getTeams("https://dev.azure.com/org", "azure-proj-1", "Bearer plain-access");
        verify(azureDevOpsClient).getIterations("https://dev.azure.com/org", "azure-proj-1", "team-1", "Bearer plain-access");
    }

    @Test
    void shouldUpsertSprintForEachIteration() {
        when(azureDevOpsClient.getTeams(anyString(), anyString(), anyString()))
                .thenReturn(List.of(new AdoTeamDto("team-1", "Backend")));
        when(azureDevOpsClient.getIterations(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(List.of(iteration("iter-1"), iteration("iter-2")));
        when(sprintRepository.findByOrganizationIdAndExternalIdAndTeamId(anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(sprintRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(connection, binding, SyncTrigger.SCHEDULED);

        verify(sprintRepository, times(2)).save(any());
    }

    @Test
    void shouldUpdateSyncLogWithItemCounts() {
        when(azureDevOpsClient.getTeams(anyString(), anyString(), anyString()))
                .thenReturn(List.of(new AdoTeamDto("team-1", "Backend")));
        when(azureDevOpsClient.getIterations(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(List.of(iteration("iter-1")));
        when(sprintRepository.findByOrganizationIdAndExternalIdAndTeamId(anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(sprintRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(connection, binding, SyncTrigger.SCHEDULED);

        ArgumentCaptor<IntegrationSyncLog> captor = ArgumentCaptor.forClass(IntegrationSyncLog.class);
        verify(syncLogRepository, times(2)).save(captor.capture());
        IntegrationSyncLog finalLog = captor.getAllValues().get(1);
        assertThat(finalLog.getItemsProcessed()).isEqualTo(1);
        assertThat(finalLog.getItemsFailed()).isEqualTo(0);
        assertThat(finalLog.getStatus()).isEqualTo(SyncStatus.SUCCESS);
    }

    @Test
    void shouldRecordSuccessWhenNoTeams() {
        when(azureDevOpsClient.getTeams(anyString(), anyString(), anyString()))
                .thenReturn(List.of());

        useCase.execute(connection, binding, SyncTrigger.SCHEDULED);

        ArgumentCaptor<IntegrationSyncLog> captor = ArgumentCaptor.forClass(IntegrationSyncLog.class);
        verify(syncLogRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues().get(1).getStatus()).isEqualTo(SyncStatus.SUCCESS);
    }

    private AdoIterationDto iteration(String id) {
        return new AdoIterationDto(id, "Sprint 1", "\\Project\\Sprint 1",
                new AdoIterationDto.Attributes("2026-01-01", "2026-01-14", "past"));
    }
}
