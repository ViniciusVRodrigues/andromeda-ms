package andromeda.sei.ms.feature.sync.application.runner;

import andromeda.sei.ms.feature.allocation.application.usecase.RefreshAllocationSuggestionsUseCase;
import andromeda.sei.ms.feature.azure.application.dto.ActivityDay;
import andromeda.sei.ms.feature.azure.application.dto.AzureWorkItemsSyncResult;
import andromeda.sei.ms.feature.azure.application.usecase.SyncAzureSprintsUseCase;
import andromeda.sei.ms.feature.azure.application.usecase.SyncAzureWorkItemsUseCase;
import andromeda.sei.ms.feature.azure.domain.AzureAuthMethod;
import andromeda.sei.ms.feature.azure.domain.AzureConnection;
import andromeda.sei.ms.feature.azure.domain.AzureConnectionRepository;
import andromeda.sei.ms.feature.azure.domain.AzureConnectionStatus;
import andromeda.sei.ms.feature.azure.domain.AzureProjectBinding;
import andromeda.sei.ms.feature.azure.domain.AzureProjectBindingRepository;
import andromeda.sei.ms.feature.azure.infrastructure.security.AzureTokenRefreshService;
import andromeda.sei.ms.feature.integration.domain.Integration;
import andromeda.sei.ms.feature.integration.domain.SyncStatus;
import andromeda.sei.ms.feature.integration.domain.SyncTrigger;
import andromeda.sei.ms.feature.sync.application.ConnectionHealthUpdater;
import andromeda.sei.ms.feature.sync.domain.BindingSyncOutcome;
import andromeda.sei.ms.feature.sync.domain.SyncTarget;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Porta os casos de {@code AzureSyncJobTest} e {@code TriggerAzureSyncUseCaseTest} (encadeamento
 * sprints → work items, isolamento de falhas, regra de saude) para o runner do orquestrador.
 */
@ExtendWith(MockitoExtension.class)
class AzureBindingSyncRunnerTest {

    private static final String ORG = "org-1";
    private static final Instant NOW = Instant.now();
    private static final Instant BACKFILL = NOW.minus(Duration.ofDays(90));

    @Mock private AzureConnectionRepository azureConnectionRepository;
    @Mock private AzureProjectBindingRepository azureProjectBindingRepository;
    @Mock private AzureTokenRefreshService azureTokenRefreshService;
    @Mock private SyncAzureSprintsUseCase syncAzureSprintsUseCase;
    @Mock private SyncAzureWorkItemsUseCase syncAzureWorkItemsUseCase;
    @Mock private ConnectionHealthUpdater connectionHealthUpdater;
    @Mock private RefreshAllocationSuggestionsUseCase refreshAllocationSuggestionsUseCase;

    @InjectMocks
    private AzureBindingSyncRunner runner;

    private final List<BindingSyncOutcome> outcomes = new ArrayList<>();

    @Test
    void shouldFeedWorkItemSyncTheBindingReturnedBySprintSync() {
        AzureConnection connection = connection(AzureConnectionStatus.CONNECTED, AzureAuthMethod.PAT);
        AzureProjectBinding original = binding("bind-1");
        AzureProjectBinding afterSprints = binding("bind-1");
        afterSprints.setLastSyncAt(NOW);
        givenConnection(connection);
        givenBinding(original);
        when(syncAzureSprintsUseCase.execute(connection, original, SyncTrigger.MANUAL)).thenReturn(afterSprints);
        when(syncAzureWorkItemsUseCase.sync(eq(connection), same(afterSprints), eq(SyncTrigger.MANUAL), eq(BACKFILL)))
                .thenReturn(new AzureWorkItemsSyncResult(afterSprints, SyncStatus.SUCCESS, 5, 0));

        runner.run(batch(SyncTrigger.MANUAL, false, target("bind-1")), outcomes::add);

        ArgumentCaptor<AzureProjectBinding> captor = ArgumentCaptor.forClass(AzureProjectBinding.class);
        verify(syncAzureWorkItemsUseCase).sync(eq(connection), captor.capture(), eq(SyncTrigger.MANUAL), eq(BACKFILL));
        assertThat(captor.getValue()).isSameAs(afterSprints);
        assertThat(captor.getValue().getWorkItemsWatermark())
                .as("sprint sync carimba lastSyncAt mas nao o watermark: o primeiro sync segue FULL")
                .isNull();
        assertThat(outcomes).singleElement().satisfies(o -> {
            assertThat(o.status()).isEqualTo(SyncStatus.SUCCESS);
            assertThat(o.itemsProcessed()).isEqualTo(5);
        });
    }

    @Test
    void shouldRefreshAllocationSuggestionsOnceWithTheUnionOfEveryBindingsActivityDays() {
        AzureConnection connection = connection(AzureConnectionStatus.CONNECTED, AzureAuthMethod.PAT);
        givenConnection(connection);
        AzureProjectBinding b1 = binding("bind-1");
        AzureProjectBinding b2 = binding("bind-2");
        givenBinding(b1);
        givenBinding(b2);
        ActivityDay d1 = new ActivityDay("u1", LocalDate.of(2026, 9, 30));
        ActivityDay d2 = new ActivityDay("u2", LocalDate.of(2026, 9, 30));
        when(syncAzureSprintsUseCase.execute(connection, b1, SyncTrigger.MANUAL)).thenReturn(b1);
        when(syncAzureSprintsUseCase.execute(connection, b2, SyncTrigger.MANUAL)).thenReturn(b2);
        when(syncAzureWorkItemsUseCase.sync(eq(connection), same(b1), any(), any())).thenReturn(
                new AzureWorkItemsSyncResult(b1, SyncStatus.SUCCESS, 1, 0, 0, 0, false, Set.of(d1)));
        when(syncAzureWorkItemsUseCase.sync(eq(connection), same(b2), any(), any())).thenReturn(
                new AzureWorkItemsSyncResult(b2, SyncStatus.SUCCESS, 1, 0, 0, 0, false, Set.of(d1, d2)));

        runner.run(batch(SyncTrigger.MANUAL, false, target("bind-1"), target("bind-2")), outcomes::add);

        verify(refreshAllocationSuggestionsUseCase, org.mockito.Mockito.times(1)).execute(ORG, Set.of(d1, d2));
    }

    @Test
    void shouldNotFailTheSyncWhenTheAllocationRefreshThrows() {
        AzureConnection connection = connection(AzureConnectionStatus.CONNECTED, AzureAuthMethod.PAT);
        givenConnection(connection);
        AzureProjectBinding b1 = binding("bind-1");
        givenBinding(b1);
        ActivityDay d1 = new ActivityDay("u1", LocalDate.of(2026, 9, 30));
        when(syncAzureSprintsUseCase.execute(connection, b1, SyncTrigger.MANUAL)).thenReturn(b1);
        when(syncAzureWorkItemsUseCase.sync(any(), any(), any(), any())).thenReturn(
                new AzureWorkItemsSyncResult(b1, SyncStatus.SUCCESS, 1, 0, 0, 0, false, Set.of(d1)));
        org.mockito.Mockito.doThrow(new RuntimeException("boom")).when(refreshAllocationSuggestionsUseCase)
                .execute(any(), any());

        runner.run(batch(SyncTrigger.MANUAL, false, target("bind-1")), outcomes::add);

        assertThat(outcomes).singleElement().satisfies(o -> assertThat(o.status()).isEqualTo(SyncStatus.SUCCESS));
    }

    @Test
    void shouldNotCallRefreshWhenNoNewActivity() {
        AzureConnection connection = connection(AzureConnectionStatus.CONNECTED, AzureAuthMethod.PAT);
        givenConnection(connection);
        givenSuccessfulSync(connection, binding("bind-1"));

        runner.run(batch(SyncTrigger.MANUAL, false, target("bind-1")), outcomes::add);

        verifyNoInteractions(refreshAllocationSuggestionsUseCase);
    }

    @Test
    void shouldRecoverConnectionWhenEveryBindingSucceeds() {
        AzureConnection connection = connection(AzureConnectionStatus.ERROR, AzureAuthMethod.PAT);
        givenConnection(connection);
        givenSuccessfulSync(connection, binding("bind-1"));

        runner.run(batch(SyncTrigger.MANUAL, false, target("bind-1")), outcomes::add);

        verify(connectionHealthUpdater).recordAzure(ORG, true);
    }

    @Test
    void shouldIsolateBindingFailureAndCountItForConnectionHealth() {
        AzureConnection connection = connection(AzureConnectionStatus.CONNECTED, AzureAuthMethod.PAT);
        givenConnection(connection);
        AzureProjectBinding broken = binding("bind-1");
        givenBinding(broken);
        when(syncAzureSprintsUseCase.execute(connection, broken, SyncTrigger.MANUAL))
                .thenThrow(new RuntimeException("azure down"));
        givenSuccessfulSync(connection, binding("bind-2"));

        runner.run(batch(SyncTrigger.MANUAL, false, target("bind-1"), target("bind-2")), outcomes::add);

        assertThat(outcomes).extracting(BindingSyncOutcome::status).containsExactly(SyncStatus.FAILED, SyncStatus.SUCCESS);
        assertThat(outcomes.getFirst().errorCode()).isEqualTo(AzureBindingSyncRunner.SYNC_FAILED);
        verify(connectionHealthUpdater).recordAzure(ORG, false);
    }

    @Test
    void shouldReportPartialWhenWorkItemsPartiallyFailWithoutHurtingConnectionHealth() {
        AzureConnection connection = connection(AzureConnectionStatus.CONNECTED, AzureAuthMethod.PAT);
        AzureProjectBinding binding = binding("bind-1");
        givenConnection(connection);
        givenBinding(binding);
        when(syncAzureSprintsUseCase.execute(connection, binding, SyncTrigger.MANUAL)).thenReturn(binding);
        when(syncAzureWorkItemsUseCase.sync(any(), any(), any(), any()))
                .thenReturn(new AzureWorkItemsSyncResult(binding, SyncStatus.PARTIAL, 8, 2));

        runner.run(batch(SyncTrigger.MANUAL, false, target("bind-1")), outcomes::add);

        assertThat(outcomes).singleElement().satisfies(o -> {
            assertThat(o.status()).isEqualTo(SyncStatus.PARTIAL);
            assertThat(o.errorCode()).isEqualTo(AzureBindingSyncRunner.WORK_ITEMS_PARTIAL);
            assertThat(o.itemsFailed()).isEqualTo(2);
        });
        verify(connectionHealthUpdater).recordAzure(ORG, true);
    }

    @Test
    void shouldFailEveryTargetWhenThereIsNoConnection() {
        when(azureConnectionRepository.findByOrganizationIdAndActiveTrue(ORG)).thenReturn(Optional.empty());

        runner.run(batch(SyncTrigger.MANUAL, false, target("bind-1"), target("bind-2")), outcomes::add);

        assertThat(outcomes).hasSize(2).allMatch(o -> o.status() == SyncStatus.FAILED
                && AzureBindingSyncRunner.CONNECTION_NOT_FOUND.equals(o.errorCode()));
        verifyNoInteractions(syncAzureSprintsUseCase, connectionHealthUpdater);
    }

    @Test
    void shouldSkipErroredConnectionOnScheduledRunsOnly() {
        givenConnection(connection(AzureConnectionStatus.ERROR, AzureAuthMethod.PAT));

        runner.run(batch(SyncTrigger.SCHEDULED, false, target("bind-1")), outcomes::add);

        assertThat(outcomes).singleElement()
                .satisfies(o -> assertThat(o.errorCode()).isEqualTo(AzureBindingSyncRunner.CONNECTION_IN_ERROR));
        verifyNoInteractions(syncAzureSprintsUseCase);
        verify(connectionHealthUpdater, never()).recordAzure(any(), anyBoolean());
    }

    @Test
    void shouldCountTokenRefreshFailureAgainstConnectionHealth() {
        AzureConnection connection = connection(AzureConnectionStatus.CONNECTED, AzureAuthMethod.OAUTH);
        givenConnection(connection);
        when(azureTokenRefreshService.refreshIfNeeded(connection)).thenThrow(new RuntimeException("refresh denied"));

        runner.run(batch(SyncTrigger.MANUAL, false, target("bind-1")), outcomes::add);

        assertThat(outcomes).singleElement()
                .satisfies(o -> assertThat(o.errorCode()).isEqualTo(AzureBindingSyncRunner.TOKEN_REFRESH_FAILED));
        verify(connectionHealthUpdater).recordAzure(ORG, false);
    }

    @Test
    void shouldResetWorkItemsWatermarkWhenRepairing() {
        AzureConnection connection = connection(AzureConnectionStatus.CONNECTED, AzureAuthMethod.PAT);
        AzureProjectBinding binding = binding("bind-1");
        binding.setWorkItemsWatermark(NOW);
        givenConnection(connection);
        givenBinding(binding);
        when(azureProjectBindingRepository.save(binding)).thenReturn(binding);
        when(syncAzureSprintsUseCase.execute(connection, binding, SyncTrigger.MANUAL)).thenReturn(binding);
        when(syncAzureWorkItemsUseCase.sync(any(), any(), any(), any()))
                .thenReturn(new AzureWorkItemsSyncResult(binding, SyncStatus.SUCCESS, 0, 0));

        runner.run(batch(SyncTrigger.MANUAL, true, target("bind-1")), outcomes::add);

        assertThat(binding.getWorkItemsWatermark()).isNull();
        verify(azureProjectBindingRepository).save(binding);
    }

    private void givenSuccessfulSync(AzureConnection connection, AzureProjectBinding binding) {
        givenBinding(binding);
        when(syncAzureSprintsUseCase.execute(connection, binding, SyncTrigger.MANUAL)).thenReturn(binding);
        when(syncAzureWorkItemsUseCase.sync(eq(connection), same(binding), eq(SyncTrigger.MANUAL), any()))
                .thenReturn(new AzureWorkItemsSyncResult(binding, SyncStatus.SUCCESS, 1, 0));
    }

    private void givenConnection(AzureConnection connection) {
        when(azureConnectionRepository.findByOrganizationIdAndActiveTrue(ORG)).thenReturn(Optional.of(connection));
    }

    private void givenBinding(AzureProjectBinding binding) {
        when(azureProjectBindingRepository.findByIdAndOrganizationIdAndActiveTrue(binding.getId(), ORG))
                .thenReturn(Optional.of(binding));
    }

    private static BindingSyncBatch batch(SyncTrigger trigger, boolean reset, SyncTarget... targets) {
        return new BindingSyncBatch(ORG, List.of(targets), trigger, BACKFILL, reset);
    }

    private static SyncTarget target(String bindingId) {
        return new SyncTarget(Integration.AZURE_DEVOPS, bindingId, "proj-" + bindingId);
    }

    private static AzureConnection connection(AzureConnectionStatus status, AzureAuthMethod authMethod) {
        return new AzureConnection("conn-1", ORG, "https://dev.azure.com/org", null, null, null, "user-1",
                status, null, 0, null, null, true, NOW, NOW, 1L, authMethod, "enc-pat");
    }

    private static AzureProjectBinding binding(String id) {
        return new AzureProjectBinding(id, ORG, "proj-" + id, "azure-" + id, "Project " + id,
                true, null, null, true, NOW, NOW, 1L);
    }
}
