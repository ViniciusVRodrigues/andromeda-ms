package andromeda.sei.ms.feature.azure.application.usecase;

import andromeda.sei.ms.feature.azure.domain.AzureAuthMethod;
import andromeda.sei.ms.feature.azure.domain.AzureConnection;
import andromeda.sei.ms.feature.azure.domain.AzureConnectionStatus;
import andromeda.sei.ms.feature.azure.domain.AzureOrgProject;
import andromeda.sei.ms.feature.azure.domain.AzureOrgProjectRepository;
import andromeda.sei.ms.feature.azure.domain.AzureOrgUserRepository;
import andromeda.sei.ms.feature.azure.infrastructure.client.AdoTeamDto;
import andromeda.sei.ms.feature.azure.infrastructure.client.AdoTeamMemberDto;
import andromeda.sei.ms.feature.azure.infrastructure.client.AzureAuthStrategyResolver;
import andromeda.sei.ms.feature.azure.infrastructure.client.AzureDevOpsClient;
import andromeda.sei.ms.feature.azure.infrastructure.client.AzureProjectDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SyncAzureOrgLevelDataUseCaseTest {

    @Mock private AzureDevOpsClient azureDevOpsClient;
    @Mock private AzureAuthStrategyResolver authStrategyResolver;
    @Mock private AzureOrgProjectRepository azureOrgProjectRepository;
    @Mock private AzureOrgUserRepository azureOrgUserRepository;

    @InjectMocks
    private SyncAzureOrgLevelDataUseCase useCase;

    private static final Instant NOW = Instant.now();

    private AzureConnection connection() {
        return new AzureConnection("conn-1", "org-1", "https://dev.azure.com/org",
                null, null, null, "user-1", AzureConnectionStatus.CONNECTED,
                null, 0, null, null, true, NOW, NOW, null,
                AzureAuthMethod.PAT, "enc-pat");
    }

    @Test
    void shouldFetchAndPersistProjectsAndUsers() {
        AzureConnection connection = connection();
        when(authStrategyResolver.resolve(connection)).thenReturn("Bearer token");
        when(azureDevOpsClient.getProjects(anyString(), anyString()))
                .thenReturn(List.of(new AzureProjectDto("azure-1", "Project One")));
        when(azureDevOpsClient.getTeams(anyString(), org.mockito.ArgumentMatchers.eq("azure-1"), anyString()))
                .thenReturn(List.of(new AdoTeamDto("team-1", "Team One")));
        when(azureDevOpsClient.getTeamMembers(anyString(), org.mockito.ArgumentMatchers.eq("azure-1"),
                org.mockito.ArgumentMatchers.eq("team-1"), anyString()))
                .thenReturn(List.of(new AdoTeamMemberDto("azure-user-1", "João Silva", "joao@empresa.com")));

        useCase.execute(connection);

        ArgumentCaptor<List<AzureOrgProject>> projectsCaptor = ArgumentCaptor.forClass(List.class);
        verify(azureOrgProjectRepository).saveAll(projectsCaptor.capture());
        assertThat(projectsCaptor.getValue()).hasSize(1);
        assertThat(projectsCaptor.getValue().get(0).getAzureProjectId()).isEqualTo("azure-1");
        assertThat(projectsCaptor.getValue().get(0).getOrganizationId()).isEqualTo("org-1");

        verify(azureOrgUserRepository).saveAll(org.mockito.ArgumentMatchers.argThat(users ->
                users.size() == 1 && users.get(0).getAzureId().equals("azure-user-1")));
    }

    @Test
    void shouldPersistProjectsQueryableWithoutFurtherClientCalls() {
        AzureConnection connection = connection();
        when(authStrategyResolver.resolve(connection)).thenReturn("Bearer token");
        when(azureDevOpsClient.getProjects(anyString(), anyString()))
                .thenReturn(List.of(new AzureProjectDto("azure-1", "Project One")));
        when(azureDevOpsClient.getTeams(anyString(), anyString(), anyString())).thenReturn(List.of());

        useCase.execute(connection);

        verify(azureOrgProjectRepository).saveAll(org.mockito.ArgumentMatchers.argThat(list -> list.size() == 1));
    }
}
