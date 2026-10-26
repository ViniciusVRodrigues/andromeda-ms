package andromeda.sei.ms.feature.sync.application;

import andromeda.sei.ms.feature.azure.domain.AzureConnectionRepository;
import andromeda.sei.ms.feature.azure.domain.AzureProjectBindingRepository;
import andromeda.sei.ms.feature.azure.application.usecase.SyncAzureSprintsUseCase;
import andromeda.sei.ms.feature.azure.application.usecase.SyncAzureWorkItemsUseCase;
import andromeda.sei.ms.feature.azure.infrastructure.security.AzureTokenRefreshService;
import andromeda.sei.ms.feature.github.domain.GithubConnectionRepository;
import andromeda.sei.ms.feature.integration.domain.Integration;
import andromeda.sei.ms.feature.integration.domain.IntegrationSyncLog;
import andromeda.sei.ms.feature.integration.domain.SyncStatus;
import andromeda.sei.ms.feature.organization.domain.OrganizationRepository;
import andromeda.sei.ms.feature.sync.application.runner.AzureBindingSyncRunner;
import andromeda.sei.ms.feature.sync.application.runner.BindingSyncBatch;
import andromeda.sei.ms.feature.sync.application.runner.BindingSyncRunner;
import andromeda.sei.ms.feature.sync.application.runner.VcsBindingSyncRunner;
import andromeda.sei.ms.feature.sync.domain.BindingSyncOutcome;
import andromeda.sei.ms.feature.sync.domain.SyncTarget;
import andromeda.sei.ms.feature.sync.domain.SyncTargetKind;
import andromeda.sei.ms.feature.sync.infrastructure.config.VcsSyncRunnerConfig;
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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.when;

/**
 * Tasks 5.5/6.1: Azure Repos bindings share {@code Integration.AZURE_DEVOPS} with Boards; the
 * orchestrator must route them to the repository runner, never to the Boards one, and the real
 * runner set must not collide on its dispatch key (a duplicate key would crash the startup — a
 * bean-wiring check, since the application cannot be smoke-started against a database here).
 */
@ExtendWith(MockitoExtension.class)
class AzureReposSyncDispatchTest {

    private static final String ORG = "org-1";

    @Mock private OrganizationRepository organizationRepository;
    @Mock private ProjectMetricsRecalculator recalculator;
    @Mock private VcsProviderRegistry providerRegistry;
    @Mock private VcsRepoBindingRepository vcsRepoBindingRepository;
    @Mock private SyncRepositoryUseCase syncRepositoryUseCase;
    @Mock private ConnectionHealthUpdater connectionHealthUpdater;
    @Mock private AzureConnectionRepository azureConnectionRepository;
    @Mock private AzureProjectBindingRepository azureProjectBindingRepository;
    @Mock private AzureTokenRefreshService azureTokenRefreshService;
    @Mock private SyncAzureSprintsUseCase syncAzureSprintsUseCase;
    @Mock private SyncAzureWorkItemsUseCase syncAzureWorkItemsUseCase;
    @Mock private andromeda.sei.ms.feature.allocation.application.usecase.RefreshAllocationSuggestionsUseCase refreshAllocationSuggestionsUseCase;
    @Mock private GithubConnectionRepository githubConnectionRepository;

    private InMemorySyncLogRepository syncLogs;

    @BeforeEach
    void setUp() {
        syncLogs = new InMemorySyncLogRepository();
    }

    @Test
    void theRealRunnerSetShouldHaveOneRunnerPerIntegrationAndKind() {
        VcsSyncRunnerConfig config = new VcsSyncRunnerConfig();
        VcsBindingSyncRunner github = config.githubRepositorySyncRunner(providerRegistry, vcsRepoBindingRepository,
                syncRepositoryUseCase, connectionHealthUpdater);
        VcsBindingSyncRunner azureRepos = config.azureReposSyncRunner(providerRegistry, vcsRepoBindingRepository,
                syncRepositoryUseCase, connectionHealthUpdater);
        AzureBindingSyncRunner boards = new AzureBindingSyncRunner(azureConnectionRepository,
                azureProjectBindingRepository, azureTokenRefreshService, syncAzureSprintsUseCase,
                syncAzureWorkItemsUseCase, connectionHealthUpdater, refreshAllocationSuggestionsUseCase);

        assertThat(List.of(boards, azureRepos, github)).extracting(BindingSyncRunner::integration,
                BindingSyncRunner::kind).containsExactly(
                org.assertj.core.groups.Tuple.tuple(Integration.AZURE_DEVOPS, SyncTargetKind.WORK_TRACKING),
                org.assertj.core.groups.Tuple.tuple(Integration.AZURE_DEVOPS, SyncTargetKind.REPOSITORY),
                org.assertj.core.groups.Tuple.tuple(Integration.GITHUB, SyncTargetKind.REPOSITORY));
        assertThatCode(() -> new SyncOrchestrator(syncLogs, new SyncCooldownService(), List.of(boards, azureRepos, github),
                recalculator, organizationRepository, Runnable::run)).doesNotThrowAnyException();
    }

    @Test
    void shouldRouteAzureRepoBindingsToTheRepositoryRunnerAfterBoards() {
        List<String> calls = new ArrayList<>();
        RecordingRunner boards = new RecordingRunner(Integration.AZURE_DEVOPS, SyncTargetKind.WORK_TRACKING, calls);
        RecordingRunner azureRepos = new RecordingRunner(Integration.AZURE_DEVOPS, SyncTargetKind.REPOSITORY, calls);
        RecordingRunner github = new RecordingRunner(Integration.GITHUB, SyncTargetKind.REPOSITORY, calls);
        SyncOrchestrator orchestrator = new SyncOrchestrator(syncLogs, new SyncCooldownService(),
                List.of(github, azureRepos, boards), recalculator, organizationRepository, Runnable::run);
        VcsRepoBinding repoBinding = new VcsRepoBinding("az-repo-1", ORG, "proj-1", "repo-1", VcsProvider.AZURE_REPOS, true);

        String syncId = orchestrator.submit(SyncRequest.projectRefresh(ORG, "proj-1", List.of(
                new SyncTarget(Integration.GITHUB, "gh-1", "proj-1"),
                SyncTargetResolver.toTarget(repoBinding),
                new SyncTarget(Integration.AZURE_DEVOPS, "az-boards-1", "proj-1"))));

        assertThat(calls).containsExactly("AZURE_DEVOPS/WORK_TRACKING:az-boards-1",
                "AZURE_DEVOPS/REPOSITORY:az-repo-1", "GITHUB/REPOSITORY:gh-1");
        assertThat(syncLogs.get(syncId).getStatus()).isEqualTo(SyncStatus.SUCCESS);
        assertThat(syncLogs.findAllByOrganizationIdAndParentSyncId(ORG, syncId))
                .filteredOn(child -> "az-repo-1".equals(child.getBindingId()))
                .extracting(IntegrationSyncLog::getIntegration).containsExactly(Integration.AZURE_DEVOPS);
    }

    @Test
    void organizationWideAzureSyncShouldIncludeTheAzureRepoBindings() {
        SyncTargetResolver resolver = new SyncTargetResolver(azureProjectBindingRepository, vcsRepoBindingRepository,
                azureConnectionRepository, githubConnectionRepository);
        when(azureProjectBindingRepository.findAllByOrganizationIdAndSyncEnabledTrueAndActiveTrue(ORG)).thenReturn(List.of());
        when(vcsRepoBindingRepository.findAllSyncEnabledByOrganizationIdAndProvider(ORG, VcsProvider.AZURE_REPOS))
                .thenReturn(List.of(new VcsRepoBinding("az-repo-1", ORG, "proj-1", "repo-1", VcsProvider.AZURE_REPOS, true)));

        assertThat(resolver.forOrganization(ORG, Integration.AZURE_DEVOPS, null)).containsExactly(
                new SyncTarget(Integration.AZURE_DEVOPS, "az-repo-1", "proj-1", SyncTargetKind.REPOSITORY));
    }

    @Test
    void aTargetWithoutExplicitKindKeepsTheIntegrationDefault() {
        assertThat(new SyncTarget(Integration.GITHUB, "b", "p").kind()).isEqualTo(SyncTargetKind.REPOSITORY);
        assertThat(new SyncTarget(Integration.AZURE_DEVOPS, "b", "p").kind()).isEqualTo(SyncTargetKind.WORK_TRACKING);
    }

    private record RecordingRunner(Integration integration, SyncTargetKind kind, List<String> calls)
            implements BindingSyncRunner {

        @Override
        public void run(BindingSyncBatch batch, Consumer<BindingSyncOutcome> onOutcome) {
            batch.targets().forEach(target -> {
                calls.add(integration + "/" + kind + ":" + target.bindingId());
                onOutcome.accept(BindingSyncOutcome.success(target, 1));
            });
        }
    }
}
