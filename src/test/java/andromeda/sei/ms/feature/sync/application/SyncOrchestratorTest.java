package andromeda.sei.ms.feature.sync.application;

import andromeda.sei.ms.feature.integration.domain.Integration;
import andromeda.sei.ms.feature.integration.domain.IntegrationSyncLog;
import andromeda.sei.ms.feature.integration.domain.SyncOperation;
import andromeda.sei.ms.feature.integration.domain.SyncStatus;
import andromeda.sei.ms.feature.integration.domain.SyncTrigger;
import andromeda.sei.ms.feature.organization.domain.Organization;
import andromeda.sei.ms.feature.organization.domain.OrganizationRepository;
import andromeda.sei.ms.feature.organization.domain.ScheduledSyncFrequency;
import andromeda.sei.ms.feature.organization.domain.SyncSettings;
import andromeda.sei.ms.feature.sync.application.runner.BindingSyncBatch;
import andromeda.sei.ms.feature.sync.application.runner.BindingSyncRunner;
import andromeda.sei.ms.feature.sync.domain.BindingSyncOutcome;
import andromeda.sei.ms.feature.sync.domain.SyncTarget;
import andromeda.sei.ms.feature.sync.domain.exception.SyncCooldownActiveException;
import andromeda.sei.ms.feature.sync.domain.exception.SyncQueueFullException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SyncOrchestratorTest {

    private static final String ORG = "org-1";
    private static final String PROJECT = "proj-1";
    private static final SyncTarget AZURE = new SyncTarget(Integration.AZURE_DEVOPS, "az-bind-1", PROJECT);
    private static final SyncTarget GITHUB = new SyncTarget(Integration.GITHUB, "gh-bind-1", PROJECT);

    @Mock private ProjectMetricsRecalculator recalculator;
    @Mock private OrganizationRepository organizationRepository;

    private InMemorySyncLogRepository syncLogs;
    private SyncCooldownService cooldown;
    private FakeRunner azureRunner;
    private FakeRunner githubRunner;

    @BeforeEach
    void setUp() {
        syncLogs = new InMemorySyncLogRepository();
        cooldown = new SyncCooldownService();
        azureRunner = new FakeRunner(Integration.AZURE_DEVOPS);
        githubRunner = new FakeRunner(Integration.GITHUB);
    }

    private SyncOrchestrator orchestrator(Executor executor) {
        return new SyncOrchestrator(syncLogs, cooldown, List.of(azureRunner, githubRunner), recalculator,
                organizationRepository, executor);
    }

    private SyncOrchestrator orchestrator() {
        return orchestrator(Runnable::run);
    }

    @Test
    void shouldSyncEveryBindingAndReportSuccessWhenAllSucceed() {
        String syncId = orchestrator().submit(SyncRequest.projectRefresh(ORG, PROJECT, List.of(AZURE, GITHUB)));

        IntegrationSyncLog parent = syncLogs.get(syncId);
        assertThat(parent.getOperation()).isEqualTo(SyncOperation.PROJECT_REFRESH);
        assertThat(parent.getStatus()).isEqualTo(SyncStatus.SUCCESS);
        assertThat(parent.getFinishedAt()).isNotNull();
        assertThat(children(syncId)).extracting(IntegrationSyncLog::getBindingId)
                .containsExactly("az-bind-1", "gh-bind-1");
        assertThat(children(syncId)).allMatch(child -> child.getStatus() == SyncStatus.SUCCESS
                && child.getOperation() == SyncOperation.SYNC_BINDING);
        verify(recalculator, times(1)).recalculate(ORG, PROJECT);
    }

    @Test
    void shouldReportPartialWithPerBindingErrorCodeWhenOneBindingFails() {
        githubRunner.respondWith(target -> BindingSyncOutcome.failed(target, "GITHUB_SYNC_FAILED", "boom"));

        String syncId = orchestrator().submit(SyncRequest.projectRefresh(ORG, PROJECT, List.of(AZURE, GITHUB)));

        assertThat(syncLogs.get(syncId).getStatus()).isEqualTo(SyncStatus.PARTIAL);
        IntegrationSyncLog failed = child(syncId, "gh-bind-1");
        assertThat(failed.getStatus()).isEqualTo(SyncStatus.FAILED);
        assertThat(failed.getErrorCode()).isEqualTo("GITHUB_SYNC_FAILED");
        verify(recalculator).recalculate(ORG, PROJECT);
    }

    @Test
    void shouldNotReportSuccessWhenABindingHadItemFailures() {
        azureRunner.respondWith(target -> BindingSyncOutcome.partial(target, "AZURE_SYNC_WORK_ITEMS_PARTIAL", 9, 1));

        String syncId = orchestrator().submit(SyncRequest.projectRefresh(ORG, PROJECT, List.of(AZURE, GITHUB)));

        assertThat(syncLogs.get(syncId).getStatus()).isEqualTo(SyncStatus.PARTIAL);
        assertThat(syncLogs.get(syncId).getItemsFailed()).isEqualTo(1);
    }

    @Test
    void shouldReportFailedAndSkipRecalculationWhenEveryBindingFails() {
        azureRunner.respondWith(target -> BindingSyncOutcome.failed(target, "AZURE_SYNC_FAILED", "boom"));
        githubRunner.respondWith(target -> BindingSyncOutcome.failed(target, "GITHUB_SYNC_FAILED", "boom"));

        String syncId = orchestrator().submit(SyncRequest.projectRefresh(ORG, PROJECT, List.of(AZURE, GITHUB)));

        assertThat(syncLogs.get(syncId).getStatus()).isEqualTo(SyncStatus.FAILED);
        verify(recalculator, never()).recalculate(any(), any());
    }

    @Test
    void shouldRejectWith429WhenEveryBindingIsInCooldown() {
        SyncOrchestrator orchestrator = orchestrator();
        orchestrator.submit(SyncRequest.projectRefresh(ORG, PROJECT, List.of(AZURE, GITHUB)));

        assertThatThrownBy(() -> orchestrator.submit(SyncRequest.projectRefresh(ORG, PROJECT, List.of(AZURE, GITHUB))))
                .isInstanceOfSatisfying(SyncCooldownActiveException.class, e -> {
                    assertThat(e.getCode()).isEqualTo("SYNC_COOLDOWN_ACTIVE");
                    assertThat(e.getRetryAfterSeconds()).isBetween(1L, 30L);
                });
    }

    @Test
    void shouldSkipBindingInCooldownAndSyncTheOthers() {
        cooldown.tryAcquire(AZURE.bindingId());

        String syncId = orchestrator().submit(SyncRequest.projectRefresh(ORG, PROJECT, List.of(AZURE, GITHUB)));

        assertThat(children(syncId)).extracting(IntegrationSyncLog::getBindingId).containsExactly("gh-bind-1");
        assertThat(azureRunner.batches).isEmpty();
        assertThat(syncLogs.get(syncId).getStatus()).isEqualTo(SyncStatus.SUCCESS);
    }

    @Test
    void shouldAcceptTwoProjectsWithDifferentBindingsBackToBack() {
        SyncOrchestrator orchestrator = orchestrator();
        SyncTarget otherProject = new SyncTarget(Integration.GITHUB, "gh-bind-2", "proj-2");

        orchestrator.submit(SyncRequest.projectRefresh(ORG, PROJECT, List.of(AZURE, GITHUB)));
        String second = orchestrator.submit(SyncRequest.projectRefresh(ORG, "proj-2", List.of(otherProject)));

        assertThat(syncLogs.get(second).getStatus()).isEqualTo(SyncStatus.SUCCESS);
    }

    @Test
    void shouldFailParentReleaseCooldownAndThrowWhenQueueIsFull() {
        Executor full = task -> { throw new RejectedExecutionException("queue full"); };

        assertThatThrownBy(() -> orchestrator(full).submit(SyncRequest.projectRefresh(ORG, PROJECT, List.of(AZURE))))
                .isInstanceOf(SyncQueueFullException.class);

        IntegrationSyncLog parent = syncLogs.all().getFirst();
        assertThat(parent.getStatus()).isEqualTo(SyncStatus.FAILED);
        assertThat(parent.getErrorCode()).isEqualTo("SYNC_QUEUE_FULL");
        assertThat(cooldown.tryAcquire(AZURE.bindingId())).isZero();
    }

    @Test
    void shouldReturnSyncIdBeforeWorkRunsInTheExecutor() {
        List<Runnable> queued = new ArrayList<>();

        String syncId = orchestrator(queued::add).submit(SyncRequest.projectRefresh(ORG, PROJECT, List.of(AZURE)));

        assertThat(syncLogs.get(syncId).getStatus()).isEqualTo(SyncStatus.RUNNING);
        assertThat(azureRunner.batches).isEmpty();
        queued.getFirst().run();
        assertThat(syncLogs.get(syncId).getStatus()).isEqualTo(SyncStatus.SUCCESS);
    }

    @Test
    void shouldCloseChildrenAsFailedWhenRunnerAborts() {
        azureRunner.throwOnRun = true;

        String syncId = orchestrator().submit(SyncRequest.projectRefresh(ORG, PROJECT, List.of(AZURE)));

        assertThat(child(syncId, "az-bind-1").getStatus()).isEqualTo(SyncStatus.FAILED);
        assertThat(child(syncId, "az-bind-1").getErrorCode()).isEqualTo(SyncOrchestrator.INTERRUPTED);
        assertThat(syncLogs.get(syncId).getStatus()).isEqualTo(SyncStatus.FAILED);
    }

    @Test
    void shouldPassOrganizationBackfillWindowAndTriggerToRunners() {
        Instant now = Instant.now();
        Organization organization = new Organization(ORG, "Org", "org", null, null, null, null, null, true, 1L, now, now);
        organization.setSyncSettings(new SyncSettings(ScheduledSyncFrequency.OFF, 30));
        when(organizationRepository.findById(ORG)).thenReturn(Optional.of(organization));

        orchestrator().submit(SyncRequest.organization(ORG, Integration.AZURE_DEVOPS, List.of(AZURE), SyncTrigger.MANUAL));

        BindingSyncBatch batch = azureRunner.batches.getFirst();
        assertThat(batch.trigger()).isEqualTo(SyncTrigger.MANUAL);
        assertThat(batch.backfillFrom()).isCloseTo(now.minus(Duration.ofDays(30)), within(Duration.ofMinutes(1)));
    }

    @Test
    void shouldCompleteImmediatelyWhenThereIsNothingToSync() {
        String syncId = orchestrator().submit(SyncRequest.organization(ORG, Integration.GITHUB, List.of(), SyncTrigger.MANUAL));

        assertThat(syncLogs.get(syncId).getStatus()).isEqualTo(SyncStatus.SUCCESS);
    }

    @Test
    void shouldSilentlySkipCooldownBindingsWhenRunningNow() {
        cooldown.tryAcquire(AZURE.bindingId());

        SyncStatus status = orchestrator().runNow(
                SyncRequest.organization(ORG, null, List.of(AZURE, GITHUB), SyncTrigger.SCHEDULED));

        assertThat(status).isEqualTo(SyncStatus.SUCCESS);
        assertThat(azureRunner.batches).isEmpty();
        assertThat(githubRunner.batches).hasSize(1);
    }

    private List<IntegrationSyncLog> children(String parentId) {
        return syncLogs.findAllByOrganizationIdAndParentSyncId(ORG, parentId);
    }

    private IntegrationSyncLog child(String parentId, String bindingId) {
        return children(parentId).stream().filter(c -> bindingId.equals(c.getBindingId())).findFirst().orElseThrow();
    }

    /** Runner falso: responde um desfecho por alvo (sucesso por padrao) e guarda os lotes recebidos. */
    private static final class FakeRunner implements BindingSyncRunner {
        private final Integration integration;
        private final List<BindingSyncBatch> batches = new ArrayList<>();
        private final Map<String, BindingSyncOutcome> fixed = new HashMap<>();
        private Function<SyncTarget, BindingSyncOutcome> responder = target -> BindingSyncOutcome.success(target, 1);
        private boolean throwOnRun;

        FakeRunner(Integration integration) {
            this.integration = integration;
        }

        void respondWith(Function<SyncTarget, BindingSyncOutcome> responder) {
            this.responder = responder;
        }

        @Override
        public Integration integration() {
            return integration;
        }

        @Override
        public void run(BindingSyncBatch batch, Consumer<BindingSyncOutcome> onOutcome) {
            batches.add(batch);
            if (throwOnRun) {
                throw new IllegalStateException("runner exploded");
            }
            batch.targets().forEach(target -> onOutcome.accept(fixed.getOrDefault(target.bindingId(), responder.apply(target))));
        }
    }
}
