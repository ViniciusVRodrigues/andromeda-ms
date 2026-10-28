package andromeda.sei.ms.feature.vcs.application.usecase;

import andromeda.sei.ms.feature.integration.domain.IntegrationSyncLog;
import andromeda.sei.ms.feature.integration.domain.IntegrationSyncLogRepository;
import andromeda.sei.ms.feature.integration.domain.SyncTrigger;
import andromeda.sei.ms.feature.organization.domain.OrganizationRepository;
import andromeda.sei.ms.feature.vcs.application.port.VcsAuthorResolver;
import andromeda.sei.ms.feature.vcs.application.port.VcsCredentials;
import andromeda.sei.ms.feature.vcs.application.port.VcsProviderClient;
import andromeda.sei.ms.feature.vcs.application.port.VcsProviderRegistry;
import andromeda.sei.ms.feature.vcs.application.port.VcsRemoteRepository;
import andromeda.sei.ms.feature.vcs.domain.VcsProvider;
import andromeda.sei.ms.feature.vcs.domain.VcsRepoBinding;
import andromeda.sei.ms.feature.vcs.domain.VcsRepository;
import andromeda.sei.ms.feature.vcs.domain.VcsRepositoryRepository;
import andromeda.sei.ms.feature.vcs.domain.VcsSyncState;
import andromeda.sei.ms.feature.vcs.infrastructure.persistence.VcsDocumentMapper;
import andromeda.sei.ms.feature.vcs.infrastructure.persistence.VcsRepoBindingDocument;
import andromeda.sei.ms.feature.vcs.infrastructure.persistence.VcsRepoBindingRepositoryImpl;
import andromeda.sei.ms.shared.testsupport.MongoIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The sync saves the same binding instance more than once (RUNNING, then OK or FAILED). With the real
 * repository each save bumps the document version, so the instance must carry it or the second write loses
 * the optimistic lock. Mocks cannot see this, so it runs on a real Mongo.
 */
@MongoIntegrationTest
class SyncRepositoryUseCaseMongoTest {

    private static final String ORG = "org-1";
    private static final VcsCredentials CREDENTIALS = new VcsCredentials(VcsProvider.GITHUB, ORG, "pat", false);

    @Autowired private MongoTemplate mongoTemplate;

    private VcsRepoBindingRepositoryImpl bindings;
    private VcsProviderClient client;
    private VcsRepositoryContentSync contentSync;
    private SyncRepositoryUseCase useCase;

    @BeforeEach
    void setUp() {
        bindings = new VcsRepoBindingRepositoryImpl(mongoTemplate, new VcsDocumentMapper());
        mongoTemplate.dropCollection(VcsRepoBindingDocument.class);

        IntegrationSyncLogRepository logs = mock(IntegrationSyncLogRepository.class);
        when(logs.save(any())).thenAnswer(inv -> inv.getArgument(0));
        VcsRepositoryRepository repositories = mock(VcsRepositoryRepository.class);
        VcsRepository repository = new VcsRepository("r1", ORG, VcsProvider.GITHUB, "1", "octo/hello");
        repository.setExternalRepoId("1");
        when(repositories.findByIdAndOrganizationId("r1", ORG)).thenReturn(Optional.of(repository));
        when(repositories.save(any())).thenAnswer(inv -> inv.getArgument(0));
        client = mock(VcsProviderClient.class);
        when(client.getRepository(any(), any())).thenReturn(new VcsRemoteRepository("1", "octo/hello", null, "main", null));
        VcsProviderRegistry registry = mock(VcsProviderRegistry.class);
        when(registry.client(VcsProvider.GITHUB)).thenReturn(client);
        when(registry.authorResolver(VcsProvider.GITHUB)).thenReturn(mock(VcsAuthorResolver.class));
        OrganizationRepository organizations = mock(OrganizationRepository.class);
        when(organizations.findById(any())).thenReturn(Optional.empty());
        contentSync = mock(VcsRepositoryContentSync.class);
        when(contentSync.syncPullRequests(any(), any(), any())).thenReturn(List.of());
        useCase = new SyncRepositoryUseCase(logs, bindings, repositories, registry, organizations, contentSync);
    }

    private VcsRepoBinding savedBinding() {
        VcsRepoBinding binding = new VcsRepoBinding(null, ORG, "p1", "r1", VcsProvider.GITHUB, true);
        return bindings.save(binding);
    }

    @Test
    void should_saveRunningThenOk_onTheSameInstance_withoutAnOptimisticLockFailure() {
        VcsRepoBinding binding = savedBinding();

        useCase.execute(CREDENTIALS, binding, SyncTrigger.MANUAL, null);

        VcsRepoBinding read = bindings.findByIdAndOrganizationIdAndActiveTrue(binding.getId(), ORG).orElseThrow();
        assertThat(read.getSyncState()).isEqualTo(VcsSyncState.OK);
        assertThat(read.getLastSyncAt()).isNotNull();
    }

    @Test
    void should_recordFailed_afterTheRunningSave_whenTheSyncThrows() {
        VcsRepoBinding binding = savedBinding();
        when(contentSync.syncCommits(any(), any())).thenThrow(new IllegalStateException("boom"));

        assertThatThrownBy(() -> useCase.execute(CREDENTIALS, binding, SyncTrigger.MANUAL, null))
                .isInstanceOf(IllegalStateException.class);

        VcsRepoBinding read = bindings.findByIdAndOrganizationIdAndActiveTrue(binding.getId(), ORG).orElseThrow();
        assertThat(read.getSyncState()).isEqualTo(VcsSyncState.FAILED);
    }
    @Test
    void should_endOk_whenTheBindingIsEditedWhileTheSyncRuns() {
        VcsRepoBinding binding = savedBinding();
        doAnswer(inv -> {
            VcsRepoBinding edited = bindings.findByIdAndOrganizationIdAndActiveTrue(binding.getId(), ORG).orElseThrow();
            edited.setSyncEnabled(false);
            bindings.save(edited);
            return 2;
        }).when(contentSync).syncCommits(any(), any());

        useCase.execute(CREDENTIALS, binding, SyncTrigger.MANUAL, null);

        VcsRepoBinding read = bindings.findByIdAndOrganizationIdAndActiveTrue(binding.getId(), ORG).orElseThrow();
        assertThat(read.getSyncState()).isEqualTo(VcsSyncState.OK);
        assertThat(read.getLastSyncAt()).isNotNull();
        assertThat(read.isSyncEnabled()).isFalse();
    }

    @Test
    void should_recordFailed_whenTheBindingIsEditedAndThenTheSyncThrows() {
        VcsRepoBinding binding = savedBinding();
        doAnswer(inv -> {
            VcsRepoBinding edited = bindings.findByIdAndOrganizationIdAndActiveTrue(binding.getId(), ORG).orElseThrow();
            edited.setSyncEnabled(false);
            bindings.save(edited);
            throw new IllegalStateException("boom");
        }).when(contentSync).syncCommits(any(), any());

        assertThatThrownBy(() -> useCase.execute(CREDENTIALS, binding, SyncTrigger.MANUAL, null))
                .isInstanceOf(IllegalStateException.class);

        VcsRepoBinding read = bindings.findByIdAndOrganizationIdAndActiveTrue(binding.getId(), ORG).orElseThrow();
        assertThat(read.getSyncState()).isEqualTo(VcsSyncState.FAILED);
        assertThat(read.isSyncEnabled()).isFalse();
    }
}
