package andromeda.sei.ms.feature.vcs.application.usecase;

import andromeda.sei.ms.feature.integration.domain.Integration;
import andromeda.sei.ms.feature.integration.domain.IntegrationSyncLog;
import andromeda.sei.ms.feature.integration.domain.IntegrationSyncLogRepository;
import andromeda.sei.ms.feature.integration.domain.SyncOperation;
import andromeda.sei.ms.feature.integration.domain.SyncStatus;
import andromeda.sei.ms.feature.vcs.application.dto.BindableRepositoryOutput;
import andromeda.sei.ms.feature.vcs.application.dto.BindableRepositoryOutput.Source;
import andromeda.sei.ms.feature.vcs.application.port.VcsBindableRepository;
import andromeda.sei.ms.feature.vcs.application.port.VcsCredentials;
import andromeda.sei.ms.feature.vcs.application.port.VcsCredentialsProvider;
import andromeda.sei.ms.feature.vcs.application.port.VcsProviderClient;
import andromeda.sei.ms.feature.vcs.application.port.VcsProviderRegistry;
import andromeda.sei.ms.feature.vcs.domain.VcsProvider;
import andromeda.sei.ms.feature.vcs.domain.VcsRepoBinding;
import andromeda.sei.ms.feature.vcs.domain.VcsRepoBindingRepository;
import andromeda.sei.ms.feature.vcs.domain.VcsRepository;
import andromeda.sei.ms.feature.vcs.domain.VcsRepositoryRepository;
import andromeda.sei.ms.feature.vcs.domain.exception.VcsConnectionNotFoundException;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListBindableRepositoriesUseCaseTest {

    private static final String ORG = "org-1";
    private static final VcsCredentials CREDENTIALS = new VcsCredentials(VcsProvider.GITHUB, ORG, "pat", false);

    @Mock private VcsProviderRegistry providerRegistry;
    @Mock private VcsCredentialsProvider credentialsProvider;
    @Mock private VcsProviderClient client;
    @Mock private VcsRepositoryRepository repositoryRepository;
    @Mock private VcsRepoBindingRepository bindingRepository;
    @Mock private IntegrationSyncLogRepository syncLogRepository;

    @InjectMocks
    private ListBindableRepositoriesUseCase useCase;

    @BeforeEach
    void setUp() {
        TenantContextHolder.set(ORG);
        lenient().when(providerRegistry.credentialsProvider(VcsProvider.GITHUB)).thenReturn(credentialsProvider);
        lenient().when(credentialsProvider.credentials(ORG)).thenReturn(Optional.of(CREDENTIALS));
        lenient().when(providerRegistry.client(VcsProvider.GITHUB)).thenReturn(client);
        lenient().when(bindingRepository.findAllActiveByOrganizationIdAndProvider(ORG, VcsProvider.GITHUB))
                .thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void should_unionLiveAndCached_dedupedByName_withLiveWinning() {
        when(client.listBindableRepositories(CREDENTIALS)).thenReturn(List.of(live("1", "octo/a"), live("2", "octo/b")));
        when(repositoryRepository.findAllByOrganizationIdAndProvider(ORG, VcsProvider.GITHUB))
                .thenReturn(List.of(stored("repo-b", "2", "octo/b"), stored("repo-c", "3", "octo/c")));

        List<BindableRepositoryOutput> repos = useCase.execute(VcsProvider.GITHUB);

        assertThat(repos).extracting(BindableRepositoryOutput::displayName).containsExactly("octo/a", "octo/b", "octo/c");
        assertThat(repos).extracting(BindableRepositoryOutput::source)
                .containsExactly(Source.LIVE, Source.LIVE, Source.CACHED);
        assertThat(repos).allSatisfy(r -> {
            assertThat(r.bindable()).isTrue();
            assertThat(r.boundToProjectId()).isNull();
            assertThat(r.provider()).isEqualTo(VcsProvider.GITHUB);
        });
    }

    @Test
    void should_markBoundRepositories_notBindable_withTheirProject() {
        when(client.listBindableRepositories(CREDENTIALS)).thenReturn(List.of(live("1", "octo/a"), live("2", "octo/b")));
        when(repositoryRepository.findAllByOrganizationIdAndProvider(ORG, VcsProvider.GITHUB))
                .thenReturn(List.of(stored("repo-a", "1", "octo/a")));
        when(bindingRepository.findAllActiveByOrganizationIdAndProvider(ORG, VcsProvider.GITHUB)).thenReturn(List.of(
                new VcsRepoBinding("b-1", ORG, "proj-9", "repo-a", VcsProvider.GITHUB, true)));

        List<BindableRepositoryOutput> repos = useCase.execute(VcsProvider.GITHUB);

        assertThat(repos.get(0).bindable()).isFalse();
        assertThat(repos.get(0).boundToProjectId()).isEqualTo("proj-9");
        assertThat(repos.get(1).bindable()).isTrue();
    }

    @Test
    void should_requireConnection() {
        when(credentialsProvider.credentials(ORG)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(VcsProvider.GITHUB))
                .isInstanceOf(VcsConnectionNotFoundException.class);
    }

    @Test
    void should_logSuccess_asListRepos() {
        when(client.listBindableRepositories(CREDENTIALS)).thenReturn(List.of(live("1", "octo/a")));

        useCase.execute(VcsProvider.GITHUB);

        IntegrationSyncLog log = capturedLog();
        assertThat(log.getIntegration()).isEqualTo(Integration.GITHUB);
        assertThat(log.getOperation()).isEqualTo(SyncOperation.LIST_REPOS);
        assertThat(log.getStatus()).isEqualTo(SyncStatus.SUCCESS);
        assertThat(log.getItemsProcessed()).isEqualTo(1);
    }

    @Test
    void should_logFailure_andRethrow_when_liveCallFails() {
        when(client.listBindableRepositories(CREDENTIALS)).thenThrow(new IllegalStateException("401"));

        assertThatThrownBy(() -> useCase.execute(VcsProvider.GITHUB)).isInstanceOf(IllegalStateException.class);

        assertThat(capturedLog().getStatus()).isEqualTo(SyncStatus.FAILED);
        assertThat(capturedLog().getErrorCode()).isEqualTo(ListBindableRepositoriesUseCase.LIST_FAILED);
    }

    private IntegrationSyncLog capturedLog() {
        ArgumentCaptor<IntegrationSyncLog> captor = ArgumentCaptor.forClass(IntegrationSyncLog.class);
        verify(syncLogRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void should_keepSameNamedAzureRepositoriesOfDifferentProjects_andHonorTheProviderBindableFlag() {
        VcsCredentials azure = new VcsCredentials(VcsProvider.AZURE_REPOS, ORG, "Basic x", false, "https://dev.azure.com/acme");
        when(providerRegistry.credentialsProvider(VcsProvider.AZURE_REPOS)).thenReturn(credentialsProvider);
        when(credentialsProvider.credentials(ORG)).thenReturn(Optional.of(azure));
        when(providerRegistry.client(VcsProvider.AZURE_REPOS)).thenReturn(client);
        VcsRepository bound = new VcsRepository("repo-1", ORG, VcsProvider.AZURE_REPOS, "g-1", "web");
        when(repositoryRepository.findAllByOrganizationIdAndProvider(ORG, VcsProvider.AZURE_REPOS)).thenReturn(List.of(bound));
        when(bindingRepository.findAllActiveByOrganizationIdAndProvider(ORG, VcsProvider.AZURE_REPOS)).thenReturn(List.of(
                new andromeda.sei.ms.feature.vcs.domain.VcsRepoBinding("b-1", ORG, "proj-a", "repo-1",
                        VcsProvider.AZURE_REPOS, true)));
        when(client.listBindableRepositories(azure)).thenReturn(List.of(
                new VcsBindableRepository("g-1", "web", "p-pay", "Payments", "main", true),
                new VcsBindableRepository("g-2", "web", "p-atl", "Atlas", "main", true),
                new VcsBindableRepository("g-3", "empty", "p-atl", "Atlas", null, false)));

        List<BindableRepositoryOutput> repos = useCase.execute(VcsProvider.AZURE_REPOS);

        assertThat(repos).extracting(BindableRepositoryOutput::externalRepoId).containsExactly("g-1", "g-2", "g-3");
        assertThat(repos).extracting(BindableRepositoryOutput::boundToProjectId).containsExactly("proj-a", null, null);
        assertThat(repos).extracting(BindableRepositoryOutput::bindable).containsExactly(false, true, false);
        assertThat(repos).extracting(BindableRepositoryOutput::externalProjectId).containsExactly("p-pay", "p-atl", "p-atl");
    }

    private static VcsBindableRepository live(String id, String name) {
        return new VcsBindableRepository(id, name, null, null, "main");
    }

    private static VcsRepository stored(String id, String externalId, String name) {
        return new VcsRepository(id, ORG, VcsProvider.GITHUB, externalId, name);
    }
}
