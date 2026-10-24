package andromeda.sei.ms.feature.azure.application.usecase;

import andromeda.sei.ms.feature.azure.domain.AzureAuthMethod;
import andromeda.sei.ms.feature.azure.domain.AzureConnection;
import andromeda.sei.ms.feature.azure.domain.AzureConnectionStatus;
import andromeda.sei.ms.feature.azure.domain.AzureOrgProject;
import andromeda.sei.ms.feature.azure.domain.AzureOrgProjectRepository;
import andromeda.sei.ms.feature.azure.domain.AzureConnectionRepository;
import andromeda.sei.ms.feature.azure.domain.AzureOrgUser;
import andromeda.sei.ms.feature.azure.domain.AzureOrgUserRepository;
import andromeda.sei.ms.feature.azure.domain.AzureOrgUserSource;
import andromeda.sei.ms.feature.azure.domain.exception.AzureEntitlementsForbiddenException;
import andromeda.sei.ms.feature.azure.infrastructure.client.AdoUserEntitlementDto;
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
    @Mock private AzureConnectionRepository azureConnectionRepository;
    @Mock private AutoLinkAzureIdentitiesUseCase autoLink;

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

    private void stubOneTeam(AdoTeamMemberDto... members) {
        when(authStrategyResolver.resolve(org.mockito.ArgumentMatchers.any(AzureConnection.class)))
                .thenReturn("Bearer token");
        when(azureDevOpsClient.getProjects(anyString(), anyString()))
                .thenReturn(List.of(new AzureProjectDto("azure-1", "Project One")));
        when(azureDevOpsClient.getTeams(anyString(), anyString(), anyString()))
                .thenReturn(List.of(new AdoTeamDto("team-1", "Team One")));
        when(azureDevOpsClient.getTeamMembers(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(List.of(members));
    }

    @SuppressWarnings("unchecked")
    private List<AzureOrgUser> savedUsers() {
        ArgumentCaptor<List<AzureOrgUser>> captor = ArgumentCaptor.forClass(List.class);
        verify(azureOrgUserRepository).saveAll(captor.capture());
        return captor.getValue();
    }

    @Test
    void shouldMergeDirectoryAndTeamWithoutDuplicatingPerson_andIncludeDirectoryOnlyPerson() {
        stubOneTeam(new AdoTeamMemberDto("id-ana", "ana team", "ana.team@x.com"));
        when(azureDevOpsClient.listUserEntitlements(anyString(), anyString())).thenReturn(List.of(
                new AdoUserEntitlementDto("id-ana", "Ana Dir", "ana@x.com", "ana@x.com", "o1"),
                new AdoUserEntitlementDto("id-pedro", "Pedro", "pedro@x.com", "pedro@x.com", "o2")));

        useCase.execute(connection());

        List<AzureOrgUser> users = savedUsers();
        assertThat(users).hasSize(2).allMatch(u -> u.getOrganizationId().equals("org-1"));
        AzureOrgUser ana = users.stream().filter(u -> u.getAzureId().equals("id-ana")).findFirst().orElseThrow();
        assertThat(ana.getSources()).containsExactlyInAnyOrder(AzureOrgUserSource.TEAM, AzureOrgUserSource.DIRECTORY);
        assertThat(ana.getDisplayName()).isEqualTo("Ana Dir");
        assertThat(ana.getAzureEmail()).isEqualTo("ana@x.com");
        AzureOrgUser pedro = users.stream().filter(u -> u.getAzureId().equals("id-pedro")).findFirst().orElseThrow();
        assertThat(pedro.getSources()).containsExactly(AzureOrgUserSource.DIRECTORY);
    }

    @Test
    void shouldWarnAndKeepTeamRows_whenDirectoryForbidden() {
        stubOneTeam(new AdoTeamMemberDto("id-ana", "Ana", "ana@x.com"));
        when(azureDevOpsClient.listUserEntitlements(anyString(), anyString()))
                .thenThrow(new AzureEntitlementsForbiddenException());
        AzureConnection connection = connection();

        useCase.execute(connection);

        List<AzureOrgUser> users = savedUsers();
        assertThat(users).hasSize(1);
        assertThat(users.get(0).getSources()).containsExactly(AzureOrgUserSource.TEAM);
        assertThat(connection.getOrgLevelSyncedAt()).isNotNull();
    }

    @Test
    void shouldRecordOrgLevelSyncedAt_andFilterExistingLookupByOrg() {
        stubOneTeam();
        when(azureDevOpsClient.listUserEntitlements(anyString(), anyString())).thenReturn(List.of());
        AzureConnection connection = connection();

        useCase.execute(connection);

        assertThat(connection.getOrgLevelSyncedAt()).isNotNull();
        verify(azureConnectionRepository).save(connection);
        verify(azureOrgUserRepository).findAllByOrganizationIdAndActiveTrue("org-1");
    }

    @Test
    void shouldMergeOntoExistingRow_keepingItsIdAndAccumulatingSources() {
        stubOneTeam();
        AzureOrgUser existing = new AzureOrgUser("row-1", "org-1", "id-ana", "Ana", "ana@x.com", "ana@x.com",
                List.of(AzureOrgUserSource.TEAM), true, NOW, NOW, 3L);
        when(azureOrgUserRepository.findAllByOrganizationIdAndActiveTrue("org-1")).thenReturn(List.of(existing));
        when(azureDevOpsClient.listUserEntitlements(anyString(), anyString())).thenReturn(List.of(
                new AdoUserEntitlementDto("id-ana", "Ana Silva", "ana@x.com", "ana@x.com", "o1")));

        useCase.execute(connection());

        List<AzureOrgUser> users = savedUsers();
        assertThat(users).hasSize(1);
        assertThat(users.get(0).getId()).isEqualTo("row-1");
        assertThat(users.get(0).getDisplayName()).isEqualTo("Ana Silva");
        assertThat(users.get(0).getSources())
                .containsExactlyInAnyOrder(AzureOrgUserSource.TEAM, AzureOrgUserSource.DIRECTORY);
    }

    @Test
    void shouldAutoLinkAfterSync_andSurviveAutoLinkFailure() {
        stubOneTeam();
        when(azureDevOpsClient.listUserEntitlements(anyString(), anyString())).thenReturn(List.of());
        org.mockito.Mockito.doThrow(new RuntimeException("boom")).when(autoLink).executeForOrganization("org-1");
        AzureConnection connection = connection();

        useCase.execute(connection);

        verify(autoLink).executeForOrganization("org-1");
        assertThat(connection.getOrgLevelSyncedAt()).isNotNull();
    }
}
