package andromeda.sei.ms.feature.azure.application.usecase;

import andromeda.sei.ms.feature.azure.application.DefaultWorkflowConfigFactory;
import andromeda.sei.ms.feature.azure.domain.AzureConnection;
import andromeda.sei.ms.feature.azure.domain.AzureConnectionStatus;
import andromeda.sei.ms.feature.azure.domain.AzureProjectBinding;
import andromeda.sei.ms.feature.azure.domain.exception.AzureSyncPageLimitExceededException;
import andromeda.sei.ms.feature.azure.infrastructure.client.AdoWorkItemDto;
import andromeda.sei.ms.feature.azure.infrastructure.client.AdoWorkItemUpdateDto;
import andromeda.sei.ms.feature.azure.infrastructure.client.AzureDevOpsClient;
import andromeda.sei.ms.feature.integration.domain.IntegrationSyncLog;
import andromeda.sei.ms.feature.integration.domain.IntegrationSyncLogRepository;
import andromeda.sei.ms.feature.integration.domain.SyncStatus;
import andromeda.sei.ms.feature.integration.domain.SyncTrigger;
import andromeda.sei.ms.feature.workitem.domain.FieldValueSource;
import andromeda.sei.ms.feature.workitem.domain.Sprint;
import andromeda.sei.ms.feature.workitem.domain.SprintRepository;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.domain.WorkItemRepository;
import andromeda.sei.ms.feature.workitem.domain.WorkItemTransition;
import andromeda.sei.ms.feature.workitem.domain.WorkItemTransitionRepository;
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
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SyncAzureWorkItemsUseCaseTest {

    @Mock private IntegrationSyncLogRepository syncLogRepository;
    @Mock private AzureAuthStrategyResolver authStrategyResolver;
    @Mock private WorkItemRepository workItemRepository;
    @Mock private WorkItemTransitionRepository workItemTransitionRepository;
    @Mock private SprintRepository sprintRepository;
    @Mock private AzureDevOpsClient azureDevOpsClient;

    @InjectMocks
    private SyncAzureWorkItemsUseCase useCase;

    private AzureConnection connection;
    private AzureProjectBinding binding;
    private static final Instant NOW = Instant.now();

    @BeforeEach
    void setUp() {
        connection = new AzureConnection("conn-1", "org-1", "https://dev.azure.com/org",
                "enc-access", "enc-refresh", NOW.plusSeconds(3600),
                "user-1", AzureConnectionStatus.CONNECTED, null, 0, null, null, true, NOW, NOW, null,
                andromeda.sei.ms.feature.azure.domain.AzureAuthMethod.OAUTH, null);

        binding = new AzureProjectBinding("bind-1", "org-1", "proj-1", "azure-proj-1", "My Project",
                true, null, DefaultWorkflowConfigFactory.forScrum(), true, NOW, NOW, null);

        IntegrationSyncLog syncLog = new IntegrationSyncLog("log-1", "org-1", null, null,
                SyncStatus.RUNNING, null, "proj-1", NOW, null, null, 0, 0, null, null);
        when(syncLogRepository.save(any())).thenReturn(syncLog);
        when(authStrategyResolver.resolve(any())).thenReturn("Bearer plain-access");
    }

    @Test
    void shouldBuildTransitionHistoryOnFirstSync() {
        when(azureDevOpsClient.queryWorkItemIdsPage(anyString(), anyString(), anyString(), isNull(), isNull(), any()))
                .thenReturn(List.of(100));
        when(azureDevOpsClient.getWorkItemsBatch(anyString(), any(), anyString()))
                .thenReturn(List.of(adoItem(100, "Active")));

        WorkItem savedItem = workItem("100", "Active");
        when(workItemRepository.findByOrganizationIdAndExternalIdAndSource(anyString(), anyString(), any()))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(savedItem));
        when(workItemRepository.save(any())).thenReturn(savedItem);
        when(workItemTransitionRepository.existsByWorkItemId(any())).thenReturn(false);
        when(workItemTransitionRepository.findAllByWorkItemId(any())).thenReturn(List.of());
        when(azureDevOpsClient.getWorkItemUpdates(anyString(), eq(100), anyString()))
                .thenReturn(List.of(stateUpdate("Active")));
        when(workItemTransitionRepository.upsertByCompositeKey(any()))
                .thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(connection, binding, SyncTrigger.SCHEDULED);

        verify(azureDevOpsClient).getWorkItemUpdates(anyString(), eq(100), anyString());
        verify(workItemTransitionRepository, atLeastOnce()).upsertByCompositeKey(any());
    }

    @Test
    void shouldAppendIncrementalTransitionWhenStatusChanges() {
        Instant lastSync = NOW.minusSeconds(3600);
        binding.setLastSyncAt(lastSync);

        when(azureDevOpsClient.queryWorkItemIdsPage(anyString(), anyString(), anyString(), eq(lastSync), isNull(), any()))
                .thenReturn(List.of(100));
        when(azureDevOpsClient.getWorkItemsBatch(anyString(), any(), anyString()))
                .thenReturn(List.of(adoItem(100, "Closed")));

        // existingStored = what's in DB before this sync (rawStatus "Active")
        WorkItem existingStored = workItem("100", "Active");
        // savedAfterFirstSave = what save() returns (same id, but we return a fresh copy to avoid mutation aliasing)
        WorkItem savedAfterFirstSave = workItem("100", "Closed");
        when(workItemRepository.findByOrganizationIdAndExternalIdAndSource(anyString(), anyString(), any()))
                .thenReturn(Optional.of(existingStored));
        when(workItemRepository.save(any())).thenReturn(savedAfterFirstSave);
        when(workItemTransitionRepository.existsByWorkItemId(any())).thenReturn(true);
        when(workItemTransitionRepository.findAllByWorkItemId(any())).thenReturn(List.of());
        when(workItemTransitionRepository.upsertByCompositeKey(any()))
                .thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(connection, binding, SyncTrigger.SCHEDULED);

        verify(azureDevOpsClient, never()).getWorkItemUpdates(anyString(), anyInt(), anyString());
        ArgumentCaptor<WorkItemTransition> captor = ArgumentCaptor.forClass(WorkItemTransition.class);
        verify(workItemTransitionRepository).upsertByCompositeKey(captor.capture());
        assertThat(captor.getValue().getToRawStatus()).isEqualTo("Closed");
        assertThat(captor.getValue().getFromRawStatus()).isEqualTo("Active");
    }

    @Test
    void shouldDeriveStartedAtAndCompletedAtFromTransitions() {
        when(azureDevOpsClient.queryWorkItemIdsPage(anyString(), anyString(), anyString(), isNull(), isNull(), any()))
                .thenReturn(List.of(100));
        when(azureDevOpsClient.getWorkItemsBatch(anyString(), any(), anyString()))
                .thenReturn(List.of(adoItem(100, "Closed")));

        WorkItem savedItem = workItem("100", "Closed");
        when(workItemRepository.findByOrganizationIdAndExternalIdAndSource(anyString(), anyString(), any()))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(savedItem));
        when(workItemRepository.save(any())).thenReturn(savedItem);
        when(workItemTransitionRepository.existsByWorkItemId(any())).thenReturn(false);
        when(azureDevOpsClient.getWorkItemUpdates(anyString(), eq(100), anyString()))
                .thenReturn(List.of(stateUpdate("Active"), stateUpdate("Closed")));
        when(workItemTransitionRepository.upsertByCompositeKey(any()))
                .thenAnswer(inv -> inv.getArgument(0));

        Instant startedAtTime = NOW.minusSeconds(1000);
        Instant completedAtTime = NOW.minusSeconds(100);
        WorkItemTransition inProgress = new WorkItemTransition(null, "org-1", null,
                null, "Active", WorkItem.ResolvedStatus.IN_PROGRESS,
                startedAtTime, NOW, true, null, NOW, NOW);
        WorkItemTransition done = new WorkItemTransition(null, "org-1", null,
                null, "Closed", WorkItem.ResolvedStatus.DONE,
                completedAtTime, NOW, true, null, NOW, NOW);

        when(workItemTransitionRepository.findAllByWorkItemId(any()))
                .thenReturn(List.of(inProgress, done));

        useCase.execute(connection, binding, SyncTrigger.SCHEDULED);

        ArgumentCaptor<WorkItem> itemCaptor = ArgumentCaptor.forClass(WorkItem.class);
        verify(workItemRepository, atLeastOnce()).save(itemCaptor.capture());
        WorkItem lastSaved = itemCaptor.getAllValues().get(itemCaptor.getAllValues().size() - 1);
        assertThat(lastSaved.getStartedAt()).isEqualTo(startedAtTime);
        assertThat(lastSaved.getCompletedAt()).isEqualTo(completedAtTime);
    }

    @Test
    void shouldApplyEffortFromDefaultStoryPointsMappingAndDualWriteStoryPoints() {
        when(azureDevOpsClient.queryWorkItemIdsPage(anyString(), anyString(), anyString(), isNull(), isNull(), any()))
                .thenReturn(List.of(100));
        AdoWorkItemDto item = new AdoWorkItemDto(100, Map.of(
                "System.Title", "My Task",
                "System.WorkItemType", "Bug",
                "System.State", "Active",
                "System.CreatedDate", NOW.toString(),
                "System.ChangedDate", NOW.toString(),
                "Microsoft.VSTS.Scheduling.StoryPoints", 5.0
        ));
        when(azureDevOpsClient.getWorkItemsBatch(anyString(), any(), anyString()))
                .thenReturn(List.of(item));

        when(workItemRepository.findByOrganizationIdAndExternalIdAndSource(anyString(), anyString(), any()))
                .thenReturn(Optional.empty());
        when(workItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(connection, binding, SyncTrigger.SCHEDULED);

        ArgumentCaptor<WorkItem> captor = ArgumentCaptor.forClass(WorkItem.class);
        verify(workItemRepository, atLeastOnce()).save(captor.capture());
        WorkItem firstSaved = captor.getAllValues().get(0);
        assertThat(firstSaved.getEffort()).isEqualTo(5.0);
        assertThat(firstSaved.getEffortSource()).isEqualTo(FieldValueSource.AZURE_SYNC);
        assertThat(firstSaved.getStoryPoints()).isEqualTo(5.0);
    }

    @Test
    void shouldSkipOverwritingManuallyOverriddenEffort() {
        when(azureDevOpsClient.queryWorkItemIdsPage(anyString(), anyString(), anyString(), isNull(), isNull(), any()))
                .thenReturn(List.of(100));
        AdoWorkItemDto item = new AdoWorkItemDto(100, Map.of(
                "System.Title", "My Task",
                "System.WorkItemType", "Bug",
                "System.State", "Active",
                "System.CreatedDate", NOW.toString(),
                "System.ChangedDate", NOW.toString(),
                "Microsoft.VSTS.Scheduling.StoryPoints", 8.0
        ));
        when(azureDevOpsClient.getWorkItemsBatch(anyString(), any(), anyString()))
                .thenReturn(List.of(item));

        WorkItem existing = workItem("100", "Active");
        existing.setEffort(13.0);
        existing.setEffortSource(FieldValueSource.MANUAL_OVERRIDE);
        when(workItemRepository.findByOrganizationIdAndExternalIdAndSource(anyString(), anyString(), any()))
                .thenReturn(Optional.of(existing));
        when(workItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(workItemTransitionRepository.existsByWorkItemId(any())).thenReturn(true);
        when(workItemTransitionRepository.findAllByWorkItemId(any())).thenReturn(List.of());

        useCase.execute(connection, binding, SyncTrigger.SCHEDULED);

        ArgumentCaptor<WorkItem> captor = ArgumentCaptor.forClass(WorkItem.class);
        verify(workItemRepository, atLeastOnce()).save(captor.capture());
        WorkItem firstSaved = captor.getAllValues().get(0);
        assertThat(firstSaved.getEffort()).isEqualTo(13.0);
        assertThat(firstSaved.getEffortSource()).isEqualTo(FieldValueSource.MANUAL_OVERRIDE);
    }

    @Test
    void shouldPaginateAcrossMultiplePagesAndStopOnShortPage() {
        List<Integer> firstPage = new java.util.ArrayList<>();
        for (int i = 1; i <= AzureDevOpsClient.WIQL_PAGE_SIZE; i++) firstPage.add(i);
        List<Integer> secondPage = List.of(AzureDevOpsClient.WIQL_PAGE_SIZE + 1);

        when(azureDevOpsClient.queryWorkItemIdsPage(anyString(), anyString(), anyString(), isNull(), isNull(), any()))
                .thenReturn(firstPage);
        when(azureDevOpsClient.queryWorkItemIdsPage(anyString(), anyString(), anyString(), isNull(),
                eq(AzureDevOpsClient.WIQL_PAGE_SIZE), any()))
                .thenReturn(secondPage);
        when(azureDevOpsClient.getWorkItemsBatch(anyString(), any(), anyString()))
                .thenReturn(List.of());

        useCase.execute(connection, binding, SyncTrigger.SCHEDULED);

        verify(azureDevOpsClient).queryWorkItemIdsPage(anyString(), anyString(), anyString(), isNull(), isNull(), any());
        verify(azureDevOpsClient).queryWorkItemIdsPage(anyString(), anyString(), anyString(), isNull(),
                eq(AzureDevOpsClient.WIQL_PAGE_SIZE), any());
        verify(azureDevOpsClient, org.mockito.Mockito.times(2))
                .queryWorkItemIdsPage(anyString(), anyString(), anyString(), isNull(), any(), any());
    }

    @Test
    void shouldResolveStatusUsingOnlyEntryMatchingBothWorkItemTypeAndRawStatus() {
        // Config has two entries sharing rawStatus "Active" but scoped to different types.
        // The Bug-scoped entry should win for a Bug work item, not the Task-scoped one.
        var config = new andromeda.sei.ms.feature.azure.domain.WorkflowConfig(
                List.of(
                        new andromeda.sei.ms.feature.azure.domain.StatusMapping("Bug", "Active", WorkItem.ResolvedStatus.IN_PROGRESS),
                        new andromeda.sei.ms.feature.azure.domain.StatusMapping("Task", "Active", WorkItem.ResolvedStatus.DONE)
                ),
                List.of(new andromeda.sei.ms.feature.azure.domain.TypeMapping("Bug", WorkItem.ResolvedType.BUG)),
                List.of(), null, andromeda.sei.ms.feature.azure.domain.WorkflowConfig.ConfigSource.CUSTOM);
        binding.setWorkflowConfig(config);

        when(azureDevOpsClient.queryWorkItemIdsPage(anyString(), anyString(), anyString(), isNull(), isNull(), any()))
                .thenReturn(List.of(100));
        when(azureDevOpsClient.getWorkItemsBatch(anyString(), any(), anyString()))
                .thenReturn(List.of(adoItem(100, "Active")));
        WorkItem savedItem = workItem("100", "Active");
        when(workItemRepository.findByOrganizationIdAndExternalIdAndSource(anyString(), anyString(), any()))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(savedItem));
        when(workItemRepository.save(any())).thenReturn(savedItem);
        when(workItemTransitionRepository.existsByWorkItemId(any())).thenReturn(false);
        when(azureDevOpsClient.getWorkItemUpdates(anyString(), eq(100), anyString()))
                .thenReturn(List.of());
        when(workItemTransitionRepository.findAllByWorkItemId(any())).thenReturn(List.of());
        when(workItemTransitionRepository.upsertByCompositeKey(any()))
                .thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(connection, binding, SyncTrigger.SCHEDULED);

        ArgumentCaptor<WorkItem> captor = ArgumentCaptor.forClass(WorkItem.class);
        verify(workItemRepository, atLeastOnce()).save(captor.capture());
        WorkItem firstSaved = captor.getAllValues().get(0);
        assertThat(firstSaved.getResolvedStatus()).isEqualTo(WorkItem.ResolvedStatus.IN_PROGRESS);
    }

    @Test
    void shouldTreatLegacyStatusMappingWithNullWorkItemTypeAsMatchingAnyType() {
        // Legacy entry has no workItemType (predates per-type scoping) -- must still match
        // a Bug work item's "Active" status.
        var config = new andromeda.sei.ms.feature.azure.domain.WorkflowConfig(
                List.of(new andromeda.sei.ms.feature.azure.domain.StatusMapping(null, "Active", WorkItem.ResolvedStatus.IN_PROGRESS)),
                List.of(new andromeda.sei.ms.feature.azure.domain.TypeMapping("Bug", WorkItem.ResolvedType.BUG)),
                List.of(), null, andromeda.sei.ms.feature.azure.domain.WorkflowConfig.ConfigSource.CUSTOM);
        binding.setWorkflowConfig(config);

        when(azureDevOpsClient.queryWorkItemIdsPage(anyString(), anyString(), anyString(), isNull(), isNull(), any()))
                .thenReturn(List.of(100));
        when(azureDevOpsClient.getWorkItemsBatch(anyString(), any(), anyString()))
                .thenReturn(List.of(adoItem(100, "Active")));
        WorkItem savedItem = workItem("100", "Active");
        when(workItemRepository.findByOrganizationIdAndExternalIdAndSource(anyString(), anyString(), any()))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(savedItem));
        when(workItemRepository.save(any())).thenReturn(savedItem);
        when(workItemTransitionRepository.existsByWorkItemId(any())).thenReturn(false);
        when(azureDevOpsClient.getWorkItemUpdates(anyString(), eq(100), anyString()))
                .thenReturn(List.of());
        when(workItemTransitionRepository.findAllByWorkItemId(any())).thenReturn(List.of());
        when(workItemTransitionRepository.upsertByCompositeKey(any()))
                .thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(connection, binding, SyncTrigger.SCHEDULED);

        ArgumentCaptor<WorkItem> captor = ArgumentCaptor.forClass(WorkItem.class);
        verify(workItemRepository, atLeastOnce()).save(captor.capture());
        WorkItem firstSaved = captor.getAllValues().get(0);
        assertThat(firstSaved.getResolvedStatus()).isEqualTo(WorkItem.ResolvedStatus.IN_PROGRESS);
    }

    @Test
    void shouldFailSyncWhenPageLimitExceeded() {
        when(azureDevOpsClient.queryWorkItemIdsPage(anyString(), anyString(), anyString(), isNull(), any(), any()))
                .thenAnswer(inv -> {
                    List<Integer> page = new java.util.ArrayList<>();
                    for (int i = 0; i < AzureDevOpsClient.WIQL_PAGE_SIZE; i++) page.add(i);
                    return page;
                });

        assertThatThrownBy(() -> useCase.execute(connection, binding, SyncTrigger.SCHEDULED))
                .isInstanceOf(AzureSyncPageLimitExceededException.class);
    }

    private AdoWorkItemDto adoItem(int id, String status) {
        return new AdoWorkItemDto(id, Map.of(
                "System.Title", "My Task",
                "System.WorkItemType", "Bug",
                "System.State", status,
                "System.CreatedDate", NOW.toString(),
                "System.ChangedDate", NOW.toString()
        ));
    }

    private AdoWorkItemUpdateDto stateUpdate(String newState) {
        return new AdoWorkItemUpdateDto(1,
                Map.of("System.State", Map.of("newValue", newState, "oldValue", "New")),
                NOW.toString());
    }

    private WorkItem workItem(String externalId, String rawStatus) {
        return new WorkItem("wi-" + externalId, "org-1", "proj-1",
                WorkItem.Source.AZURE_DEVOPS, externalId, NOW, "Title",
                "Bug", WorkItem.ResolvedType.BUG, rawStatus, WorkItem.ResolvedStatus.IN_PROGRESS,
                null, null, List.of(), null, null, null, null, null,
                NOW, null, null, null, null, null, true, null, NOW,
                null, null, null, null, null, null, null, null);
    }
}
