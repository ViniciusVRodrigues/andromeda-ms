package andromeda.sei.ms.feature.sync.application.runner;

import andromeda.sei.ms.feature.github.domain.exception.GithubPermissionDeniedException;
import andromeda.sei.ms.feature.integration.domain.Integration;
import andromeda.sei.ms.feature.integration.domain.SyncStatus;
import andromeda.sei.ms.feature.integration.domain.SyncTrigger;
import andromeda.sei.ms.feature.sync.application.ConnectionHealthUpdater;
import andromeda.sei.ms.feature.sync.domain.BindingSyncOutcome;
import andromeda.sei.ms.feature.sync.domain.SyncTarget;
import andromeda.sei.ms.feature.vcs.VcsLegacyFixtures;
import andromeda.sei.ms.feature.vcs.application.port.VcsCredentials;
import andromeda.sei.ms.feature.vcs.application.port.VcsCredentialsProvider;
import andromeda.sei.ms.feature.vcs.application.port.VcsProviderRegistry;
import andromeda.sei.ms.feature.vcs.application.usecase.SyncRepositoryUseCase;
import andromeda.sei.ms.feature.vcs.domain.VcsProvider;
import andromeda.sei.ms.feature.vcs.domain.VcsRepoBinding;
import andromeda.sei.ms.feature.vcs.domain.VcsRepoBindingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** The cases of the former {@code GithubBindingSyncRunnerTest}, now through the provider ports. */
@ExtendWith(MockitoExtension.class)
class VcsBindingSyncRunnerTest {

    private static final String ORG = "org-1";
    private static final Instant NOW = Instant.now();
    private static final Instant BACKFILL = NOW.minus(Duration.ofDays(90));

    @Mock private VcsProviderRegistry providerRegistry;
    @Mock private VcsCredentialsProvider credentialsProvider;
    @Mock private VcsRepoBindingRepository bindingRepository;
    @Mock private SyncRepositoryUseCase syncRepositoryUseCase;
    @Mock private ConnectionHealthUpdater connectionHealthUpdater;

    private VcsBindingSyncRunner runner;
    private final List<BindingSyncOutcome> outcomes = new ArrayList<>();

    @BeforeEach
    void setUp() {
        runner = new VcsBindingSyncRunner(VcsProvider.GITHUB, providerRegistry, bindingRepository, syncRepositoryUseCase,
                connectionHealthUpdater);
        lenient().when(providerRegistry.credentialsProvider(VcsProvider.GITHUB)).thenReturn(credentialsProvider);
    }

    @Test
    void shouldRunForTheGithubIntegration() {
        assertThat(runner.integration()).isEqualTo(Integration.GITHUB);
    }

    @Test
    void shouldSyncEveryBindingWithTriggerAndBackfillWindow() {
        VcsCredentials credentials = credentials(false);
        VcsRepoBinding binding = binding("bind-1");
        givenCredentials(credentials);
        givenBinding(binding);
        when(syncRepositoryUseCase.execute(credentials, binding, SyncTrigger.MANUAL, BACKFILL)).thenReturn(12);

        runner.run(batch(SyncTrigger.MANUAL, false, target("bind-1")), outcomes::add);

        assertThat(outcomes).singleElement().satisfies(o -> {
            assertThat(o.status()).isEqualTo(SyncStatus.SUCCESS);
            assertThat(o.itemsProcessed()).isEqualTo(12);
        });
    }

    @Test
    void shouldIsolateOneBindingFailureAndContinueWithTheRest() {
        VcsCredentials credentials = credentials(false);
        VcsRepoBinding broken = binding("bind-1");
        VcsRepoBinding healthy = binding("bind-2");
        givenCredentials(credentials);
        givenBinding(broken);
        givenBinding(healthy);
        when(syncRepositoryUseCase.execute(credentials, broken, SyncTrigger.MANUAL, BACKFILL))
                .thenThrow(new GithubPermissionDeniedException("no scope"));

        runner.run(batch(SyncTrigger.MANUAL, false, target("bind-1"), target("bind-2")), outcomes::add);

        assertThat(outcomes).extracting(BindingSyncOutcome::status).containsExactly(SyncStatus.FAILED, SyncStatus.SUCCESS);
        assertThat(outcomes.getFirst().errorCode()).isEqualTo("GITHUB_PERMISSION_DENIED");
        verify(syncRepositoryUseCase).execute(credentials, healthy, SyncTrigger.MANUAL, BACKFILL);
        verify(connectionHealthUpdater).recordVcs(VcsProvider.GITHUB, ORG, false);
    }

    @Test
    void shouldRecoverErroredConnectionWhenARefreshSyncsEveryBinding() {
        givenCredentials(credentials(true));
        givenBinding(binding("bind-1"));

        runner.run(batch(SyncTrigger.MANUAL, false, target("bind-1")), outcomes::add);

        verify(connectionHealthUpdater).recordVcs(VcsProvider.GITHUB, ORG, true);
    }

    @Test
    void shouldNotCallProviderForErroredConnectionOnScheduledRun() {
        givenCredentials(credentials(true));

        runner.run(batch(SyncTrigger.SCHEDULED, false, target("bind-1")), outcomes::add);

        assertThat(outcomes).singleElement()
                .satisfies(o -> assertThat(o.errorCode()).isEqualTo(VcsBindingSyncRunner.CONNECTION_IN_ERROR));
        verifyNoInteractions(syncRepositoryUseCase, connectionHealthUpdater);
    }

    @Test
    void shouldFailEveryTargetWhenThereIsNoConnection() {
        when(credentialsProvider.credentials(ORG)).thenReturn(Optional.empty());

        runner.run(batch(SyncTrigger.MANUAL, false, target("bind-1")), outcomes::add);

        assertThat(outcomes).singleElement()
                .satisfies(o -> assertThat(o.errorCode()).isEqualTo(VcsBindingSyncRunner.CONNECTION_NOT_FOUND));
        verifyNoInteractions(syncRepositoryUseCase);
    }

    @Test
    void shouldReportBindingNotFound() {
        givenCredentials(credentials(false));
        when(bindingRepository.findByIdAndOrganizationIdAndActiveTrue("gone", ORG)).thenReturn(Optional.empty());

        runner.run(batch(SyncTrigger.MANUAL, false, target("gone")), outcomes::add);

        assertThat(outcomes).singleElement()
                .satisfies(o -> assertThat(o.errorCode()).isEqualTo("REPO_BINDING_NOT_FOUND"));
    }

    @Test
    void shouldClearWatermarkWhenRepairing() {
        VcsCredentials credentials = credentials(false);
        VcsRepoBinding binding = binding("bind-1");
        binding.setLastSyncAt(NOW);
        givenCredentials(credentials);
        givenBinding(binding);
        when(bindingRepository.save(binding)).thenReturn(binding);

        runner.run(batch(SyncTrigger.MANUAL, true, target("bind-1")), outcomes::add);

        assertThat(binding.getLastSyncAt()).isNull();
        verify(syncRepositoryUseCase).execute(credentials, binding, SyncTrigger.MANUAL, BACKFILL);
    }

    @Test
    void azureReposRunnerShouldFileUnderAzureDevOpsAsARepositoryRunner() {
        VcsBindingSyncRunner azure = new VcsBindingSyncRunner(VcsProvider.AZURE_REPOS, providerRegistry,
                bindingRepository, syncRepositoryUseCase, connectionHealthUpdater);

        assertThat(azure.integration()).isEqualTo(Integration.AZURE_DEVOPS);
        assertThat(azure.kind()).isEqualTo(andromeda.sei.ms.feature.sync.domain.SyncTargetKind.REPOSITORY);
    }

    @Test
    void aMissingCodeScopeShouldFailTheBindingWithoutHurtingTheSharedConnection() {
        VcsBindingSyncRunner azure = new VcsBindingSyncRunner(VcsProvider.AZURE_REPOS, providerRegistry,
                bindingRepository, syncRepositoryUseCase, connectionHealthUpdater);
        VcsCredentials credentials = new VcsCredentials(VcsProvider.AZURE_REPOS, ORG, "Basic x", false,
                "https://dev.azure.com/acme");
        VcsRepoBinding binding = binding("bind-1");
        when(providerRegistry.credentialsProvider(VcsProvider.AZURE_REPOS)).thenReturn(credentialsProvider);
        givenCredentials(credentials);
        givenBinding(binding);
        when(syncRepositoryUseCase.execute(credentials, binding, SyncTrigger.SCHEDULED, BACKFILL))
                .thenThrow(new andromeda.sei.ms.feature.azure.domain.exception.AzureReposScopeMissingException());

        azure.run(batch(SyncTrigger.SCHEDULED, false, target("bind-1")), outcomes::add);

        assertThat(outcomes).singleElement().satisfies(o -> {
            assertThat(o.status()).isEqualTo(SyncStatus.FAILED);
            assertThat(o.errorCode()).isEqualTo("AZURE_REPOS_SCOPE_MISSING");
        });
        verify(connectionHealthUpdater).recordVcs(VcsProvider.AZURE_REPOS, ORG, true);
    }

    @Test
    void aCredentialThatCannotBeObtainedShouldFailEveryTarget() {
        when(credentialsProvider.credentials(ORG)).thenThrow(
                new andromeda.sei.ms.feature.azure.domain.exception.AzureTokenRefreshFailedException(ORG));

        runner.run(batch(SyncTrigger.MANUAL, false, target("bind-1"), target("bind-2")), outcomes::add);

        assertThat(outcomes).hasSize(2).allSatisfy(o -> assertThat(o.errorCode()).isEqualTo("AZURE_TOKEN_REFRESH_FAILED"));
        verifyNoInteractions(syncRepositoryUseCase);
        verify(connectionHealthUpdater).recordVcs(VcsProvider.GITHUB, ORG, false);
    }

    private void givenCredentials(VcsCredentials credentials) {
        when(credentialsProvider.credentials(ORG)).thenReturn(Optional.of(credentials));
    }

    private void givenBinding(VcsRepoBinding binding) {
        when(bindingRepository.findByIdAndOrganizationIdAndActiveTrue(binding.getId(), ORG))
                .thenReturn(Optional.of(binding));
    }

    private static BindingSyncBatch batch(SyncTrigger trigger, boolean reset, SyncTarget... targets) {
        return new BindingSyncBatch(ORG, List.of(targets), trigger, BACKFILL, reset);
    }

    private static SyncTarget target(String bindingId) {
        return new SyncTarget(Integration.GITHUB, bindingId, "proj-1");
    }

    private static VcsCredentials credentials(boolean inError) {
        return new VcsCredentials(VcsProvider.GITHUB, ORG, "pat", inError);
    }

    private static VcsRepoBinding binding(String id) {
        return VcsLegacyFixtures.binding(id, ORG, "proj-1", "repo-" + id, true, null, true, NOW, NOW, 1L);
    }
}
