package andromeda.sei.ms.feature.vcs.application.usecase;

import andromeda.sei.ms.feature.integration.domain.Integration;
import andromeda.sei.ms.feature.integration.domain.IntegrationSyncLog;
import andromeda.sei.ms.feature.integration.domain.IntegrationSyncLogRepository;
import andromeda.sei.ms.feature.integration.domain.SyncOperation;
import andromeda.sei.ms.feature.integration.domain.SyncStatus;
import andromeda.sei.ms.feature.integration.domain.SyncTrigger;
import andromeda.sei.ms.feature.organization.domain.OrganizationRepository;
import andromeda.sei.ms.feature.vcs.application.port.VcsAuthorResolver;
import andromeda.sei.ms.feature.vcs.application.port.VcsCredentials;
import andromeda.sei.ms.feature.vcs.application.port.VcsProviderClient;
import andromeda.sei.ms.feature.vcs.application.port.VcsProviderRegistry;
import andromeda.sei.ms.feature.vcs.application.port.VcsRemoteRepository;
import andromeda.sei.ms.feature.vcs.domain.VcsProvider;
import andromeda.sei.ms.feature.vcs.domain.VcsPullRequest;
import andromeda.sei.ms.feature.vcs.domain.VcsRepoBinding;
import andromeda.sei.ms.feature.vcs.domain.VcsRepoBindingRepository;
import andromeda.sei.ms.feature.vcs.domain.VcsRepository;
import andromeda.sei.ms.feature.vcs.domain.VcsRepositoryRepository;
import andromeda.sei.ms.feature.vcs.domain.exception.VcsProviderUnsupportedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** vcs-core task 4.5: the neutral sync orchestration of one binding. */
@ExtendWith(MockitoExtension.class)
class SyncRepositoryUseCaseTest {

    private static final String ORG = "org-1";
    private static final Instant BACKFILL = Instant.parse("2026-07-02T00:00:00Z");
    private static final VcsCredentials CREDENTIALS = new VcsCredentials(VcsProvider.GITHUB, ORG, "pat", false);

    @Mock private IntegrationSyncLogRepository syncLogRepository;
    @Mock private VcsRepoBindingRepository bindingRepository;
    @Mock private VcsRepositoryRepository repositoryRepository;
    @Mock private VcsProviderRegistry providerRegistry;
    @Mock private OrganizationRepository organizationRepository;
    @Mock private VcsRepositoryContentSync contentSync;
    @Mock private VcsProviderClient client;
    @Mock private VcsAuthorResolver authors;

    private SyncRepositoryUseCase useCase;
    private final List<IntegrationSyncLog> savedLogs = new ArrayList<>();
    private VcsRepository repository;

    @BeforeEach
    void setUp() {
        useCase = new SyncRepositoryUseCase(syncLogRepository, bindingRepository, repositoryRepository,
                providerRegistry, organizationRepository, contentSync);
        repository = new VcsRepository("repo-1", ORG, VcsProvider.GITHUB, "123", "octo/hello");
        lenient().when(syncLogRepository.save(any())).thenAnswer(inv -> {
            IntegrationSyncLog log = inv.getArgument(0);
            savedLogs.add(log);
            return log;
        });
        lenient().when(providerRegistry.client(VcsProvider.GITHUB)).thenReturn(client);
        lenient().when(providerRegistry.authorResolver(VcsProvider.GITHUB)).thenReturn(authors);
        lenient().when(organizationRepository.findById(ORG)).thenReturn(Optional.empty());
        lenient().when(repositoryRepository.findByIdAndOrganizationId("repo-1", ORG)).thenReturn(Optional.of(repository));
        lenient().when(repositoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(client.getRepository(eq(CREDENTIALS), any())).thenReturn(
                new VcsRemoteRepository("123", "octo/hello", null, "main", Instant.parse("2026-09-01T00:00:00Z")));
    }

    @Test
    void should_advanceWatermarkToTheInstantTheSyncStarted_and_countItems() {
        VcsRepoBinding binding = binding(Instant.parse("2026-09-01T00:00:00Z"));
        when(contentSync.syncCommits(any(), eq(binding.getLastSyncAt()))).thenReturn(3);
        when(contentSync.syncPullRequests(any(), eq(binding.getLastSyncAt()), eq(BACKFILL))).thenReturn(List.of(pr(), pr()));
        when(contentSync.syncReviewEvents(any(), any(), eq(binding.getLastSyncAt()))).thenReturn(4);
        Instant before = Instant.now();

        int processed = useCase.execute(CREDENTIALS, binding, SyncTrigger.MANUAL, BACKFILL);

        assertThat(processed).isEqualTo(1 + 3 + 2 + 4);
        ArgumentCaptor<VcsRepoBinding> saved = ArgumentCaptor.forClass(VcsRepoBinding.class);
        verify(bindingRepository).save(saved.capture());
        assertThat(saved.getValue().getLastSyncAt()).isBetween(before, Instant.now());
        assertThat(savedLogs.getLast().getStatus()).isEqualTo(SyncStatus.SUCCESS);
        assertThat(savedLogs.getLast().getIntegration()).isEqualTo(Integration.GITHUB);
        assertThat(savedLogs.getLast().getOperation()).isEqualTo(SyncOperation.SYNC_REPOS);
        assertThat(repository.getDefaultBranch()).isEqualTo("main");
    }

    @Test
    void should_readCommitsFromBackfillWindow_when_firstSync() {
        VcsRepoBinding binding = binding(null);

        useCase.execute(CREDENTIALS, binding, SyncTrigger.MANUAL, BACKFILL);

        verify(contentSync).syncCommits(any(), eq(BACKFILL));
        verify(contentSync).syncPullRequests(any(), eq(null), eq(BACKFILL));
    }

    @Test
    void should_readCommitsSinceWatermark_when_incrementalSync() {
        Instant watermark = Instant.now().minus(Duration.ofHours(2));
        VcsRepoBinding binding = binding(watermark);

        useCase.execute(CREDENTIALS, binding, SyncTrigger.SCHEDULED, BACKFILL);

        verify(contentSync).syncCommits(any(), eq(watermark));
    }

    @Test
    void should_notAdvanceWatermark_and_logFailure_when_syncFails() {
        VcsRepoBinding binding = binding(null);
        when(contentSync.syncCommits(any(), any())).thenThrow(new IllegalStateException("provider down"));

        assertThatThrownBy(() -> useCase.execute(CREDENTIALS, binding, SyncTrigger.MANUAL, BACKFILL))
                .isInstanceOf(IllegalStateException.class);

        verify(bindingRepository, never()).save(any());
        assertThat(binding.getLastSyncAt()).isNull();
        assertThat(savedLogs.getLast().getStatus()).isEqualTo(SyncStatus.FAILED);
        assertThat(savedLogs.getLast().getErrorCode()).isEqualTo(SyncRepositoryUseCase.SYNC_FAILED);
    }

    @Test
    void should_failWithProviderUnsupported_when_noImplementationRegistered() {
        VcsRepoBinding binding = binding(null);
        when(providerRegistry.client(VcsProvider.GITHUB)).thenThrow(new VcsProviderUnsupportedException("GITHUB"));

        assertThatThrownBy(() -> useCase.execute(CREDENTIALS, binding, SyncTrigger.MANUAL, BACKFILL))
                .isInstanceOf(VcsProviderUnsupportedException.class)
                .hasFieldOrPropertyWithValue("code", "VCS_PROVIDER_UNSUPPORTED");
        verifyNoInteractions(contentSync);
    }

    @Test
    void should_fillExternalId_when_repositoryWasPendingFirstSync() {
        repository.setExternalRepoId(null);
        when(repositoryRepository.findByOrganizationIdAndProviderAndExternalRepoId(ORG, VcsProvider.GITHUB, "123"))
                .thenReturn(Optional.empty());

        useCase.execute(CREDENTIALS, binding(null), SyncTrigger.MANUAL, BACKFILL);

        assertThat(repository.getExternalRepoId()).isEqualTo("123");
        assertThat(repository.isPendingFirstSync()).isFalse();
    }

    @Test
    void should_mergePendingIntoExistingRepository_when_externalIdAlreadyKnown() {
        repository.setExternalRepoId(null);
        VcsRepository existing = new VcsRepository("repo-9", ORG, VcsProvider.GITHUB, "123", "octo/hello");
        when(repositoryRepository.findByOrganizationIdAndProviderAndExternalRepoId(ORG, VcsProvider.GITHUB, "123"))
                .thenReturn(Optional.of(existing));
        VcsRepoBinding binding = binding(null);

        useCase.execute(CREDENTIALS, binding, SyncTrigger.MANUAL, BACKFILL);

        assertThat(binding.getRepositoryId()).isEqualTo("repo-9");
        assertThat(repository.isActive()).isFalse();
        verify(repositoryRepository).save(repository);
        verify(repositoryRepository).save(existing);
    }

    private static VcsRepoBinding binding(Instant lastSyncAt) {
        VcsRepoBinding binding = new VcsRepoBinding("bind-1", ORG, "proj-1", "repo-1", VcsProvider.GITHUB, true);
        binding.setLastSyncAt(lastSyncAt);
        return binding;
    }

    private static VcsPullRequest pr() {
        return new VcsPullRequest("pr", ORG, "repo-1", VcsProvider.GITHUB, "1", 1, "t", null);
    }
}
