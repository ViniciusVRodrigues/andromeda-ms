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
    @Mock private andromeda.sei.ms.feature.user.domain.UserRepository userRepository;
    @Mock private andromeda.sei.ms.feature.azure.domain.AzureProjectBindingRepository azureProjectBindingRepository;
    @Mock private andromeda.sei.ms.feature.azure.application.port.AzureOrgUserCollector orgUserCollector;
    @Mock private andromeda.sei.ms.feature.workitem.domain.WorkItemActivityRepository workItemActivityRepository;

    private SyncAzureWorkItemsUseCase useCase;

    private AzureConnection connection;
    private AzureProjectBinding binding;
    private static final Instant NOW = Instant.now();

    @BeforeEach
    void setUp() {
        useCase = new SyncAzureWorkItemsUseCase(syncLogRepository, authStrategyResolver, workItemRepository,
                workItemTransitionRepository, sprintRepository, azureDevOpsClient, userRepository,
                azureProjectBindingRepository, orgUserCollector,
                new andromeda.sei.ms.feature.azure.application.service.AzureWorkItemRemovalReconciler(
                        workItemRepository, azureDevOpsClient),
                workItemActivityRepository);
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
        /* O que dispara o modo incremental é o watermark, não lastSyncAt. */
        binding.setWorkItemsWatermark(lastSync);

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

        // /updates now feeds the activity only; the transition is still built from the state compare
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
    void shouldApplyComplexityFromDefaultStoryPointsMappingOnStoryTierItems() {
        when(azureDevOpsClient.queryWorkItemIdsPage(anyString(), anyString(), anyString(), isNull(), isNull(), any()))
                .thenReturn(List.of(100));
        AdoWorkItemDto item = new AdoWorkItemDto(100, Map.of(
                "System.Title", "My Task",
                "System.WorkItemType", "Bug",
                "System.State", "Active",
                "System.CreatedDate", NOW.toString(),
                "System.ChangedDate", NOW.toString(),
                "Microsoft.VSTS.Scheduling.StoryPoints", 5.0
        ), null);
        when(azureDevOpsClient.getWorkItemsBatch(anyString(), any(), anyString()))
                .thenReturn(List.of(item));

        when(workItemRepository.findByOrganizationIdAndExternalIdAndSource(anyString(), anyString(), any()))
                .thenReturn(Optional.empty());
        when(workItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(connection, binding, SyncTrigger.SCHEDULED);

        ArgumentCaptor<WorkItem> captor = ArgumentCaptor.forClass(WorkItem.class);
        verify(workItemRepository, atLeastOnce()).save(captor.capture());
        WorkItem firstSaved = captor.getAllValues().get(0);
        // story-tier (Bug) + default mapping: StoryPoints is Complexity; Effort is for Tasks (CompletedWork)
        assertThat(firstSaved.getComplexity()).isEqualTo(5.0);
        assertThat(firstSaved.getComplexitySource()).isEqualTo(FieldValueSource.AZURE_SYNC);
        assertThat(firstSaved.getEffort()).isNull();
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
        ), null);
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
    void shouldExtractParentExternalIdFromHierarchyReverseRelation() {
        when(azureDevOpsClient.queryWorkItemIdsPage(anyString(), anyString(), anyString(), isNull(), isNull(), any()))
                .thenReturn(List.of(100));
        AdoWorkItemDto item = new AdoWorkItemDto(100, Map.of(
                "System.Title", "My Task",
                "System.WorkItemType", "Task",
                "System.State", "Active",
                "System.CreatedDate", NOW.toString(),
                "System.ChangedDate", NOW.toString()
        ), List.of(new AdoWorkItemDto.AdoWorkItemRelationDto(
                "System.LinkTypes.Hierarchy-Reverse",
                "https://dev.azure.com/org/_apis/wit/workItems/42")));
        when(azureDevOpsClient.getWorkItemsBatch(anyString(), any(), anyString()))
                .thenReturn(List.of(item));

        WorkItem savedItem = workItem("100", "Active");
        when(workItemRepository.findByOrganizationIdAndExternalIdAndSource(anyString(), anyString(), any()))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(savedItem));
        when(workItemRepository.save(any())).thenReturn(savedItem);
        when(workItemTransitionRepository.existsByWorkItemId(any())).thenReturn(false);
        when(workItemTransitionRepository.findAllByWorkItemId(any())).thenReturn(List.of());
        when(azureDevOpsClient.getWorkItemUpdates(anyString(), eq(100), anyString())).thenReturn(List.of());
        when(workItemTransitionRepository.upsertByCompositeKey(any()))
                .thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(connection, binding, SyncTrigger.SCHEDULED);

        ArgumentCaptor<WorkItem> captor = ArgumentCaptor.forClass(WorkItem.class);
        verify(workItemRepository, atLeastOnce()).save(captor.capture());
        WorkItem firstSaved = captor.getAllValues().get(0);
        assertThat(firstSaved.getParentExternalId()).isEqualTo("42");
    }

    @Test
    void shouldLeaveParentExternalIdNullWhenNoRelationsPresent() {
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
        when(azureDevOpsClient.getWorkItemUpdates(anyString(), eq(100), anyString())).thenReturn(List.of());
        when(workItemTransitionRepository.upsertByCompositeKey(any()))
                .thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(connection, binding, SyncTrigger.SCHEDULED);

        ArgumentCaptor<WorkItem> captor = ArgumentCaptor.forClass(WorkItem.class);
        verify(workItemRepository, atLeastOnce()).save(captor.capture());
        WorkItem firstSaved = captor.getAllValues().get(0);
        assertThat(firstSaved.getParentExternalId()).isNull();
    }

    @Test
    void shouldLeaveParentExternalIdNullWhenRelationsHasOnlyUnrelatedLinkTypes() {
        when(azureDevOpsClient.queryWorkItemIdsPage(anyString(), anyString(), anyString(), isNull(), isNull(), any()))
                .thenReturn(List.of(100));
        AdoWorkItemDto item = new AdoWorkItemDto(100, Map.of(
                "System.Title", "My Task",
                "System.WorkItemType", "Task",
                "System.State", "Active",
                "System.CreatedDate", NOW.toString(),
                "System.ChangedDate", NOW.toString()
        ), List.of(new AdoWorkItemDto.AdoWorkItemRelationDto(
                "System.LinkTypes.Related",
                "https://dev.azure.com/org/_apis/wit/workItems/99")));
        when(azureDevOpsClient.getWorkItemsBatch(anyString(), any(), anyString()))
                .thenReturn(List.of(item));

        WorkItem savedItem = workItem("100", "Active");
        when(workItemRepository.findByOrganizationIdAndExternalIdAndSource(anyString(), anyString(), any()))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(savedItem));
        when(workItemRepository.save(any())).thenReturn(savedItem);
        when(workItemTransitionRepository.existsByWorkItemId(any())).thenReturn(false);
        when(workItemTransitionRepository.findAllByWorkItemId(any())).thenReturn(List.of());
        when(azureDevOpsClient.getWorkItemUpdates(anyString(), eq(100), anyString())).thenReturn(List.of());
        when(workItemTransitionRepository.upsertByCompositeKey(any()))
                .thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(connection, binding, SyncTrigger.SCHEDULED);

        ArgumentCaptor<WorkItem> captor = ArgumentCaptor.forClass(WorkItem.class);
        verify(workItemRepository, atLeastOnce()).save(captor.capture());
        WorkItem firstSaved = captor.getAllValues().get(0);
        assertThat(firstSaved.getParentExternalId()).isNull();
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
    void shouldResolveTypeMappedToFeature() {
        var config = new andromeda.sei.ms.feature.azure.domain.WorkflowConfig(
                List.of(new andromeda.sei.ms.feature.azure.domain.StatusMapping("Feature", "Active", WorkItem.ResolvedStatus.IN_PROGRESS)),
                List.of(new andromeda.sei.ms.feature.azure.domain.TypeMapping("Feature", WorkItem.ResolvedType.FEATURE)),
                List.of(), null, andromeda.sei.ms.feature.azure.domain.WorkflowConfig.ConfigSource.CUSTOM);
        binding.setWorkflowConfig(config);

        when(azureDevOpsClient.queryWorkItemIdsPage(anyString(), anyString(), anyString(), isNull(), isNull(), any()))
                .thenReturn(List.of(100));
        AdoWorkItemDto item = new AdoWorkItemDto(100, Map.of(
                "System.Title", "My Feature",
                "System.WorkItemType", "Feature",
                "System.State", "Active",
                "System.CreatedDate", NOW.toString(),
                "System.ChangedDate", NOW.toString()
        ), null);
        when(azureDevOpsClient.getWorkItemsBatch(anyString(), any(), anyString()))
                .thenReturn(List.of(item));
        WorkItem savedItem = new WorkItem("wi-100", "org-1", "proj-1",
                WorkItem.Source.AZURE_DEVOPS, "100", NOW, "My Feature",
                "Feature", WorkItem.ResolvedType.FEATURE, "Active", WorkItem.ResolvedStatus.IN_PROGRESS,
                null, null, List.of(), null, null, null, null, null,
                NOW, null, null, null, null, null, true, null, NOW,
                null, null, null, null, null, null, null, null);
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
        assertThat(firstSaved.getResolvedType()).isEqualTo(WorkItem.ResolvedType.FEATURE);
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


    // ---- azure-itens-excluidos: reconciliacao de removidos ----

    private static final Instant WATERMARK = NOW.minusSeconds(3600);

    /** Incremental: the since=watermark listing returns changed; the unrestricted one returns present. */
    private void stubWiql(List<Integer> changed, List<Integer> present) {
        binding.setWorkItemsWatermark(WATERMARK);
        when(azureDevOpsClient.queryWorkItemIdsPage(anyString(), anyString(), anyString(), eq(WATERMARK), isNull(), any()))
                .thenReturn(changed);
        when(azureDevOpsClient.queryWorkItemIdsPage(anyString(), anyString(), anyString(), isNull(), isNull(), any()))
                .thenReturn(present);
    }

    private void stubActiveInAndromeda(String... externalIds) {
        when(workItemRepository.findActiveExternalIdsByOrganizationIdAndProjectIdAndSource(
                "org-1", "proj-1", WorkItem.Source.AZURE_DEVOPS)).thenReturn(java.util.Set.of(externalIds));
    }

    private void stubProcessing(WorkItem stored) {
        when(workItemRepository.findByOrganizationIdAndExternalIdAndSource(anyString(), eq(stored.getExternalId()), any()))
                .thenReturn(Optional.of(stored));
        when(workItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(workItemTransitionRepository.existsByWorkItemId(any())).thenReturn(true);
        when(workItemTransitionRepository.findAllByWorkItemId(any())).thenReturn(List.of());
    }

    @Test
    void shouldDeactivateActiveItemMissingFromWiqlAsDeletedInAzure() {
        stubWiql(List.of(), List.of(1));
        stubActiveInAndromeda("1", "2");
        when(azureDevOpsClient.getDeletedWorkItems(anyString(), eq("azure-proj-1"), eq(List.of(2)), anyString()))
                .thenReturn(java.util.Set.of(2));
        when(workItemRepository.deactivateByExternalIds(eq("org-1"), eq("proj-1"), eq(WorkItem.Source.AZURE_DEVOPS),
                eq(java.util.Set.of("2")), eq(WorkItem.DeactivationReason.DELETED_IN_AZURE), any())).thenReturn(1L);

        var result = useCase.sync(connection, binding, SyncTrigger.SCHEDULED, null);

        assertThat(result.status()).isEqualTo(SyncStatus.SUCCESS);
        assertThat(result.itemsDeactivated()).isEqualTo(1);
        assertThat(result.reconciliationSkipped()).isFalse();
        verify(azureDevOpsClient, never()).getWorkItemsBatch(anyString(), any(), anyString());
    }

    @Test
    void shouldDeactivateAsLeftBindingWhenNotInRecycleBin() {
        stubWiql(List.of(), List.of(1));
        stubActiveInAndromeda("1", "2");
        when(azureDevOpsClient.getDeletedWorkItems(anyString(), anyString(), any(), anyString()))
                .thenReturn(java.util.Set.of());
        when(workItemRepository.deactivateByExternalIds(any(), any(), any(), eq(java.util.Set.of("2")),
                eq(WorkItem.DeactivationReason.LEFT_BINDING), any())).thenReturn(1L);

        assertThat(useCase.sync(connection, binding, SyncTrigger.SCHEDULED, null).itemsDeactivated()).isEqualTo(1);
    }

    @Test
    void shouldDeactivateAsUnknownAndStaySuccessWhenRecycleBinFails() {
        stubWiql(List.of(), List.of(1));
        stubActiveInAndromeda("1", "2");
        when(azureDevOpsClient.getDeletedWorkItems(anyString(), anyString(), any(), anyString()))
                .thenThrow(new RuntimeException("403"));
        when(workItemRepository.deactivateByExternalIds(any(), any(), any(), eq(java.util.Set.of("2")),
                eq(WorkItem.DeactivationReason.UNKNOWN), any())).thenReturn(1L);

        var result = useCase.sync(connection, binding, SyncTrigger.SCHEDULED, null);

        assertThat(result.status()).isEqualTo(SyncStatus.SUCCESS);
        assertThat(result.itemsDeactivated()).isEqualTo(1);
    }

    @Test
    void shouldNotDeactivateAnythingWhenIdListingFails_andKeepSyncing() {
        binding.setWorkItemsWatermark(WATERMARK);
        when(azureDevOpsClient.queryWorkItemIdsPage(anyString(), anyString(), anyString(), eq(WATERMARK), isNull(), any()))
                .thenReturn(List.of(100));
        when(azureDevOpsClient.queryWorkItemIdsPage(anyString(), anyString(), anyString(), isNull(), isNull(), any()))
                .thenThrow(new RuntimeException("wiql down"));
        when(azureDevOpsClient.getWorkItemsBatch(anyString(), any(), anyString()))
                .thenReturn(List.of(adoItem(100, "Active")));
        stubProcessing(workItem("100", "Active"));

        var result = useCase.sync(connection, binding, SyncTrigger.SCHEDULED, null);

        assertThat(result.status()).isEqualTo(SyncStatus.SUCCESS);
        assertThat(result.processed()).isEqualTo(1);
        assertThat(result.reconciliationSkipped()).isTrue();
        verify(workItemRepository, never()).deactivateByExternalIds(any(), any(), any(), any(), any(), any());
        verify(workItemRepository, never()).findActiveExternalIdsByOrganizationIdAndProjectIdAndSource(any(), any(), any());
    }

    @Test
    void shouldNotDeactivateAnythingWhenIdListingIsEmpty() {
        stubWiql(List.of(), List.of());

        var result = useCase.sync(connection, binding, SyncTrigger.SCHEDULED, null);

        assertThat(result.status()).isEqualTo(SyncStatus.SUCCESS);
        assertThat(result.reconciliationSkipped()).isTrue();
        verify(workItemRepository, never()).deactivateByExternalIds(any(), any(), any(), any(), any(), any());
    }

    @Test
    void shouldNotDeactivateAnythingWhenIdListingHitsPageLimit() {
        binding.setWorkItemsWatermark(WATERMARK);
        when(azureDevOpsClient.queryWorkItemIdsPage(anyString(), anyString(), anyString(), eq(WATERMARK), isNull(), any()))
                .thenReturn(List.of());
        when(azureDevOpsClient.queryWorkItemIdsPage(anyString(), anyString(), anyString(), isNull(), any(), any()))
                .thenAnswer(inv -> {
                    List<Integer> page = new java.util.ArrayList<>();
                    for (int i = 0; i < AzureDevOpsClient.WIQL_PAGE_SIZE; i++) page.add(i);
                    return page;
                });

        var result = useCase.sync(connection, binding, SyncTrigger.SCHEDULED, null);

        assertThat(result.status()).isEqualTo(SyncStatus.SUCCESS);
        assertThat(result.reconciliationSkipped()).isTrue();
        verify(workItemRepository, never()).deactivateByExternalIds(any(), any(), any(), any(), any(), any());
    }

    @Test
    void shouldFetchAndReactivateInactiveItemPresentInWiqlEvenWithEmptyIncremental() {
        stubWiql(List.of(), List.of(7));
        WorkItem inactive = workItem("7", "Active");
        inactive.setActive(false);
        inactive.setDeactivatedAt(NOW);
        inactive.setDeactivationReason(WorkItem.DeactivationReason.DELETED_IN_AZURE);
        when(workItemRepository.findInactiveExternalIdsByOrganizationIdAndSourceAndExternalIdIn(
                eq("org-1"), eq(WorkItem.Source.AZURE_DEVOPS), any())).thenReturn(java.util.Set.of("7"));
        when(azureDevOpsClient.getWorkItemsBatch(anyString(), eq(List.of(7)), anyString()))
                .thenReturn(List.of(adoItem(7, "Active")));
        stubProcessing(inactive);
        stubActiveInAndromeda();

        var result = useCase.sync(connection, binding, SyncTrigger.SCHEDULED, null);

        assertThat(result.itemsReactivated()).isEqualTo(1);
        assertThat(result.processed()).isEqualTo(1);
        ArgumentCaptor<WorkItem> captor = ArgumentCaptor.forClass(WorkItem.class);
        verify(workItemRepository, atLeastOnce()).save(captor.capture());
        WorkItem saved = captor.getAllValues().get(0);
        assertThat(saved.isActive()).isTrue();
        assertThat(saved.getDeactivatedAt()).isNull();
        assertThat(saved.getDeactivationReason()).isNull();
    }

    @Test
    void shouldNotFetchIdPresentInWiqlThatHasNoDocumentInAndromeda() {
        stubWiql(List.of(), List.of(9));
        when(workItemRepository.findInactiveExternalIdsByOrganizationIdAndSourceAndExternalIdIn(
                anyString(), any(), any())).thenReturn(java.util.Set.of());
        stubActiveInAndromeda();

        useCase.sync(connection, binding, SyncTrigger.SCHEDULED, null);

        verify(azureDevOpsClient, never()).getWorkItemsBatch(anyString(), any(), anyString());
    }

    @Test
    void shouldNotDeactivateItemThatArrivedThroughTheIncrementalBatch() {
        /* the id listing was read before the item existed: 100 is in the batch but not in the listing */
        stubWiql(List.of(100), List.of(1));
        when(azureDevOpsClient.getWorkItemsBatch(anyString(), any(), anyString()))
                .thenReturn(List.of(adoItem(100, "Active")));
        stubProcessing(workItem("100", "Active"));
        when(workItemRepository.findInactiveExternalIdsByOrganizationIdAndSourceAndExternalIdIn(
                anyString(), any(), any())).thenReturn(java.util.Set.of());
        stubActiveInAndromeda("1", "100");

        var result = useCase.sync(connection, binding, SyncTrigger.SCHEDULED, null);

        assertThat(result.itemsDeactivated()).isZero();
        verify(workItemRepository, never()).deactivateByExternalIds(any(), any(), any(), any(), any(), any());
        verify(azureDevOpsClient, never()).getDeletedWorkItems(any(), any(), any(), any());
    }

    @Test
    void shouldRecordDeactivatedAndReactivatedCountsInSyncLogAndResult() {
        stubWiql(List.of(), List.of(1, 7));
        stubActiveInAndromeda("1", "2", "3");
        WorkItem inactive = workItem("7", "Active");
        inactive.setActive(false);
        when(workItemRepository.findInactiveExternalIdsByOrganizationIdAndSourceAndExternalIdIn(
                anyString(), any(), any())).thenReturn(java.util.Set.of("7"));
        when(azureDevOpsClient.getWorkItemsBatch(anyString(), eq(List.of(7)), anyString()))
                .thenReturn(List.of(adoItem(7, "Active")));
        stubProcessing(inactive);
        when(azureDevOpsClient.getDeletedWorkItems(anyString(), anyString(), any(), anyString()))
                .thenReturn(java.util.Set.of(2, 3));
        when(workItemRepository.deactivateByExternalIds(any(), any(), any(), any(),
                eq(WorkItem.DeactivationReason.DELETED_IN_AZURE), any())).thenReturn(2L);

        var result = useCase.sync(connection, binding, SyncTrigger.SCHEDULED, null);

        assertThat(result.itemsDeactivated()).isEqualTo(2);
        assertThat(result.itemsReactivated()).isEqualTo(1);
        ArgumentCaptor<IntegrationSyncLog> logs = ArgumentCaptor.forClass(IntegrationSyncLog.class);
        verify(syncLogRepository, atLeastOnce()).save(logs.capture());
        IntegrationSyncLog last = logs.getAllValues().get(logs.getAllValues().size() - 1);
        assertThat(last.getItemsDeactivated()).isEqualTo(2);
        assertThat(last.getItemsReactivated()).isEqualTo(1);
    }

    private AdoWorkItemDto adoItem(int id, String status) {
        return new AdoWorkItemDto(id, Map.of(
                "System.Title", "My Task",
                "System.WorkItemType", "Bug",
                "System.State", status,
                "System.CreatedDate", NOW.toString(),
                "System.ChangedDate", NOW.toString()
        ), null);
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

    /**
     * Regressão f5eb6c7 na unidade: binding novo cujo `lastSyncAt` já foi carimbado pelo sync
     * de sprints (que roda antes) precisa mesmo assim consultar o WIQL SEM filtro de data.
     * Antes do watermark próprio, esse cenário virava incremental com `since = agora` e
     * importava só o que mudou no dia.
     */
    @Test
    void shouldRunFullSyncWhenOnlyLastSyncAtIsSet() {
        binding.setLastSyncAt(NOW);
        binding.setWorkItemsWatermark(null);

        when(azureDevOpsClient.queryWorkItemIdsPage(anyString(), anyString(), anyString(), isNull(), isNull(), any()))
                .thenReturn(List.of());

        useCase.execute(connection, binding, SyncTrigger.SCHEDULED);

        verify(azureDevOpsClient).queryWorkItemIdsPage(
                anyString(), anyString(), anyString(), isNull(), isNull(), any());
    }

    /**
     * O watermark gravado é o instante ANTERIOR à leitura. Gravar o instante final deixaria
     * de fora, para sempre, qualquer item editado durante a execução do sync.
     */
    @Test
    void shouldStampWatermarkWithInstantCapturedBeforeReading() {
        when(azureDevOpsClient.queryWorkItemIdsPage(anyString(), anyString(), anyString(), isNull(), isNull(), any()))
                .thenReturn(List.of());
        when(azureProjectBindingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Instant antes = Instant.now();
        useCase.execute(connection, binding, SyncTrigger.SCHEDULED);
        Instant depois = Instant.now();

        ArgumentCaptor<AzureProjectBinding> captor = ArgumentCaptor.forClass(AzureProjectBinding.class);
        verify(azureProjectBindingRepository).save(captor.capture());
        Instant watermark = captor.getValue().getWorkItemsWatermark();

        assertThat(watermark).isNotNull();
        assertThat(watermark).isBetween(antes, depois);
        /* lastSyncAt continua existindo, como marcador de exibição. */
        assertThat(captor.getValue().getLastSyncAt()).isNotNull();
    }

    /**
     * Falha no meio do sync não pode avançar o watermark: a próxima execução tem que reler
     * a mesma janela, senão o que ficou para trás some para sempre.
     */
    @Test
    void shouldNotAdvanceWatermarkWhenSyncFails() {
        Instant anterior = NOW.minusSeconds(7200);
        binding.setWorkItemsWatermark(anterior);

        when(azureDevOpsClient.queryWorkItemIdsPage(anyString(), anyString(), anyString(), eq(anterior), isNull(), any()))
                .thenThrow(new RuntimeException("Azure fora do ar"));

        /* O caso de uso registra a falha no log e repropaga — quem orquestra é que decide. */
        assertThatThrownBy(() -> useCase.execute(connection, binding, SyncTrigger.SCHEDULED))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Azure fora do ar");

        verify(azureProjectBindingRepository, never()).save(any());
        assertThat(binding.getWorkItemsWatermark()).isEqualTo(anterior);
    }

    // ---------------------------------------------------------------------------------------
    // metrics-correctness 1.3 — iteration shared by two teams (one sprint document per team)
    // ---------------------------------------------------------------------------------------

    @Test
    void should_linkItemToCanonicalTwin_andNotCountItFailed_when_twoTeamsShareTheIteration() {
        String path = "My Project\\Sprint 25";
        Sprint teamB = new Sprint("sprint-team-b", "org-1", "proj-1", WorkItem.Source.AZURE_DEVOPS, "iter-25",
                "team-b", "Team B", "Sprint 25", path, NOW, NOW, Sprint.TimeFrame.CURRENT, true, 0L,
                NOW.plusSeconds(1), NOW);
        Sprint teamA = new Sprint("sprint-team-a", "org-1", "proj-1", WorkItem.Source.AZURE_DEVOPS, "iter-25",
                "team-a", "Team A", "Sprint 25", path, NOW, NOW, Sprint.TimeFrame.CURRENT, true, 0L, NOW, NOW);
        when(sprintRepository.findAllByOrganizationIdAndProjectIdAndPath("org-1", "proj-1", path))
                .thenReturn(List.of(teamB, teamA));
        when(azureDevOpsClient.queryWorkItemIdsPage(anyString(), anyString(), anyString(), isNull(), isNull(), any()))
                .thenReturn(List.of(100));
        Map<String, Object> fields = new java.util.HashMap<>(adoItem(100, "Active").fields());
        fields.put("System.IterationPath", path);
        when(azureDevOpsClient.getWorkItemsBatch(anyString(), any(), anyString()))
                .thenReturn(List.of(new AdoWorkItemDto(100, fields, null)));
        when(workItemRepository.findByOrganizationIdAndExternalIdAndSource(anyString(), anyString(), any()))
                .thenReturn(Optional.empty());
        when(workItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(workItemTransitionRepository.findAllByWorkItemId(any())).thenReturn(List.of());
        when(azureProjectBindingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = useCase.sync(connection, binding, SyncTrigger.MANUAL, null);

        assertThat(result.status()).isEqualTo(SyncStatus.SUCCESS);
        assertThat(result.processed()).isEqualTo(1);
        assertThat(result.failed()).isZero();
        ArgumentCaptor<WorkItem> saved = ArgumentCaptor.forClass(WorkItem.class);
        verify(workItemRepository, atLeastOnce()).save(saved.capture());
        assertThat(saved.getValue().getSprintId()).isEqualTo("sprint-team-a");
        assertThat(saved.getValue().getSprintName()).isEqualTo("Sprint 25");
    }

    // ---------------------------------------------------------------------------------------
    // on-demand-refresh 2.5 / 8.3 — PARTIAL por item e janela de backfill no primeiro sync
    // ---------------------------------------------------------------------------------------

    @Test
    void shouldReportPartialWhenSomeItemsFailAndOthersSucceed() {
        stubTwoItemsWhereSecondFails(false);

        var result = useCase.sync(connection, binding, SyncTrigger.MANUAL, null);

        assertThat(result.status()).isEqualTo(SyncStatus.PARTIAL);
        assertThat(result.processed()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
        ArgumentCaptor<IntegrationSyncLog> logCaptor = ArgumentCaptor.forClass(IntegrationSyncLog.class);
        verify(syncLogRepository, atLeastOnce()).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getStatus()).isEqualTo(SyncStatus.PARTIAL);
    }

    @Test
    void shouldReportFailedWhenEveryItemFails() {
        stubTwoItemsWhereSecondFails(true);

        var result = useCase.sync(connection, binding, SyncTrigger.MANUAL, null);

        assertThat(result.status()).isEqualTo(SyncStatus.FAILED);
        assertThat(result.processed()).isZero();
        assertThat(result.failed()).isEqualTo(2);
    }

    @Test
    void shouldBoundFirstSyncByBackfillWindow() {
        Instant backfillFrom = NOW.minus(java.time.Duration.ofDays(90));
        binding.setWorkItemsWatermark(null);
        when(azureDevOpsClient.queryWorkItemIdsPage(anyString(), anyString(), anyString(), eq(backfillFrom), isNull(), any()))
                .thenReturn(List.of());

        useCase.sync(connection, binding, SyncTrigger.MANUAL, backfillFrom);

        verify(azureDevOpsClient).queryWorkItemIdsPage(
                anyString(), anyString(), anyString(), eq(backfillFrom), isNull(), any());
    }

    @Test
    void shouldIgnoreBackfillWindowOnIncrementalSync() {
        Instant watermark = NOW.minusSeconds(3600);
        binding.setWorkItemsWatermark(watermark);
        when(azureDevOpsClient.queryWorkItemIdsPage(anyString(), anyString(), anyString(), eq(watermark), isNull(), any()))
                .thenReturn(List.of());

        useCase.sync(connection, binding, SyncTrigger.MANUAL, NOW.minus(java.time.Duration.ofDays(90)));

        verify(azureDevOpsClient).queryWorkItemIdsPage(
                anyString(), anyString(), anyString(), eq(watermark), isNull(), any());
    }

    private void stubTwoItemsWhereSecondFails(boolean firstFailsToo) {
        when(azureDevOpsClient.queryWorkItemIdsPage(anyString(), anyString(), anyString(), isNull(), isNull(), any()))
                .thenReturn(List.of(100, 101));
        when(azureDevOpsClient.getWorkItemsBatch(anyString(), any(), anyString()))
                .thenReturn(List.of(adoItem(100, "Active"), adoItem(101, "Active")));
        when(azureProjectBindingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        if (firstFailsToo) {
            when(workItemRepository.findByOrganizationIdAndExternalIdAndSource(anyString(), anyString(), any()))
                    .thenThrow(new RuntimeException("item quebrado"));
            return;
        }
        WorkItem savedItem = workItem("100", "Active");
        when(workItemRepository.findByOrganizationIdAndExternalIdAndSource(anyString(), eq("100"), any()))
                .thenReturn(Optional.of(savedItem));
        when(workItemRepository.findByOrganizationIdAndExternalIdAndSource(anyString(), eq("101"), any()))
                .thenThrow(new RuntimeException("item quebrado"));
        when(workItemRepository.save(any())).thenReturn(savedItem);
        when(workItemTransitionRepository.existsByWorkItemId(any())).thenReturn(true);
        when(workItemTransitionRepository.findAllByWorkItemId(any())).thenReturn(List.of());
    }

    private WorkItem syncOneItemAndCaptureSaved(WorkItem stored, Map<String, Object> extraFields) {
        when(azureDevOpsClient.queryWorkItemIdsPage(anyString(), anyString(), anyString(), isNull(), isNull(), any()))
                .thenReturn(List.of(100));
        java.util.HashMap<String, Object> fields = new java.util.HashMap<>(Map.of(
                "System.Title", "t", "System.WorkItemType", "Bug", "System.State", "Active",
                "System.CreatedDate", NOW.toString(), "System.ChangedDate", NOW.toString()));
        fields.putAll(extraFields);
        when(azureDevOpsClient.getWorkItemsBatch(anyString(), any(), anyString()))
                .thenReturn(List.of(new AdoWorkItemDto(100, fields, null)));
        when(workItemRepository.findByOrganizationIdAndExternalIdAndSource(anyString(), anyString(), any()))
                .thenReturn(Optional.of(stored));
        when(workItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(workItemTransitionRepository.existsByWorkItemId(any())).thenReturn(true);
        when(workItemTransitionRepository.findAllByWorkItemId(any())).thenReturn(List.of());

        useCase.execute(connection, binding, SyncTrigger.SCHEDULED);

        ArgumentCaptor<WorkItem> captor = ArgumentCaptor.forClass(WorkItem.class);
        verify(workItemRepository, atLeastOnce()).save(captor.capture());
        return captor.getValue();
    }

    private andromeda.sei.ms.feature.user.domain.User userOf(String id, String orgId) {
        andromeda.sei.ms.feature.user.domain.User u = org.mockito.Mockito.mock(andromeda.sei.ms.feature.user.domain.User.class);
        org.mockito.Mockito.lenient().when(u.getId()).thenReturn(id);
        org.mockito.Mockito.lenient().when(u.getOrganizationId()).thenReturn(orgId);
        return u;
    }

    @Test
    void shouldClearAssigneeUserIdWhenReassignedToSomeoneWithoutAndromedaUser() {
        WorkItem stored = workItem("100", "Active");
        stored.setAssigneeUserId("u1");
        Map<String, Object> other = Map.of("id", "az-9", "displayName", "Outro", "uniqueName", "outro@x.com");
        when(userRepository.findByOrganizationIdAndAzureId("org-1", "az-9")).thenReturn(Optional.empty());
        when(userRepository.findAllByOrganizationIdAndEmailIgnoringCase("org-1", "outro@x.com")).thenReturn(List.of());

        WorkItem saved = syncOneItemAndCaptureSaved(stored, Map.of("System.AssignedTo", other));

        assertThat(saved.getAssigneeUserId()).isNull();
    }

    @Test
    void shouldClearAssigneeUserIdWhenItemHasNoAssignee() {
        WorkItem stored = workItem("100", "Active");
        stored.setAssigneeUserId("u1");

        WorkItem saved = syncOneItemAndCaptureSaved(stored, Map.of());

        assertThat(saved.getAssigneeUserId()).isNull();
    }

    @Test
    void shouldMatchAssigneeByEmailWhenAzureIdHasNoUser() {
        WorkItem stored = workItem("100", "Active");
        Map<String, Object> pedro = Map.of("id", "az-1", "displayName", "Pedro", "uniqueName", "Pedro@X.com");
        when(userRepository.findByOrganizationIdAndAzureId("org-1", "az-1")).thenReturn(Optional.empty());
        var u = userOf("u7", "org-1");
        when(userRepository.findAllByOrganizationIdAndEmailIgnoringCase("org-1", "Pedro@X.com")).thenReturn(List.of(u));

        WorkItem saved = syncOneItemAndCaptureSaved(stored, Map.of("System.AssignedTo", pedro));

        assertThat(saved.getAssigneeUserId()).isEqualTo("u7");
    }

    @Test
    void shouldNotMatchAssigneeByEmailOfUserFromAnotherOrganization() {
        WorkItem stored = workItem("100", "Active");
        Map<String, Object> pedro = Map.of("id", "az-1", "displayName", "Pedro", "uniqueName", "pedro@x.com");
        when(userRepository.findByOrganizationIdAndAzureId("org-1", "az-1")).thenReturn(Optional.empty());
        // the lookup is scoped to the organization: a user of org-2 is never returned
        when(userRepository.findAllByOrganizationIdAndEmailIgnoringCase("org-1", "pedro@x.com")).thenReturn(List.of());

        WorkItem saved = syncOneItemAndCaptureSaved(stored, Map.of("System.AssignedTo", pedro));

        assertThat(saved.getAssigneeUserId()).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldCollectIdentitiesSeenInWorkItems_inASingleBatch_andSurviveCollectorFailure() {
        when(azureDevOpsClient.queryWorkItemIdsPage(anyString(), anyString(), anyString(), isNull(), isNull(), any()))
                .thenReturn(List.of(100, 101));
        Map<String, Object> pedro = Map.of("id", "p-1", "displayName", "Pedro", "uniqueName", "pedro@x.com");
        Map<String, Object> group = Map.of("id", "g-1", "displayName", "Grp", "uniqueName", "[P]\\T",
                "isContainer", true);
        when(azureDevOpsClient.getWorkItemsBatch(anyString(), any(), anyString())).thenReturn(List.of(
                new AdoWorkItemDto(100, Map.of("System.Title", "a", "System.WorkItemType", "Bug",
                        "System.State", "Active", "System.CreatedDate", NOW.toString(),
                        "System.ChangedDate", NOW.toString(), "System.AssignedTo", pedro,
                        "System.ChangedBy", group), null),
                new AdoWorkItemDto(101, Map.of("System.Title", "b", "System.WorkItemType", "Bug",
                        "System.State", "Active", "System.CreatedDate", NOW.toString(),
                        "System.ChangedDate", NOW.toString(), "System.CreatedBy", pedro), null)));
        WorkItem saved = workItem("100", "Active");
        when(workItemRepository.findByOrganizationIdAndExternalIdAndSource(anyString(), anyString(), any()))
                .thenReturn(Optional.of(saved));
        when(workItemRepository.save(any())).thenReturn(saved);
        when(workItemTransitionRepository.existsByWorkItemId(any())).thenReturn(true);
        when(workItemTransitionRepository.findAllByWorkItemId(any())).thenReturn(List.of());
        org.mockito.Mockito.doThrow(new RuntimeException("boom")).when(orgUserCollector).collect(anyString(), any());

        var result = useCase.sync(connection, binding, SyncTrigger.SCHEDULED, null);

        assertThat(result.status()).isEqualTo(SyncStatus.SUCCESS);
        ArgumentCaptor<java.util.Collection<andromeda.sei.ms.feature.azure.application.port.AzureOrgUserCollector.ObservedIdentity>> captor =
                ArgumentCaptor.forClass(java.util.Collection.class);
        verify(orgUserCollector).collect(eq("org-1"), captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        var seen = captor.getValue().iterator().next();
        assertThat(seen.azureId()).isEqualTo("p-1");
        assertThat(seen.source()).isEqualTo(andromeda.sei.ms.feature.azure.domain.AzureOrgUserSource.WORKITEM);
    }

    // ---- work item activity (alocacao-no-dashboard MS-1) ----

    private AdoWorkItemUpdateDto revision(int rev, String azureId, String uniqueName, String changedDate,
                                          String... changedFields) {
        Map<String, Object> fields = new java.util.LinkedHashMap<>();
        fields.put("System.ChangedDate", Map.of("newValue", changedDate));
        for (String f : changedFields) fields.put(f, Map.of("newValue", "x"));
        return new AdoWorkItemUpdateDto(rev, fields, "9999-01-01T00:00:00Z",
                new AdoWorkItemUpdateDto.RevisedBy(azureId, uniqueName, "Alguem"));
    }

    /** Revisions from..to alternating authors az-1 (odd) and az-2 (even), one day per revision. */
    private List<AdoWorkItemUpdateDto> revisions(int from, int to) {
        List<AdoWorkItemUpdateDto> list = new java.util.ArrayList<>();
        for (int i = from; i <= to; i++) {
            list.add(revision(i, i % 2 == 0 ? "az-2" : "az-1", i % 2 == 0 ? "b@x.com" : "a@x.com",
                    "2026-10-0" + i + "T10:00:00Z", "System.History"));
        }
        return list;
    }

    /**
     * Syncs one item whose Azure state equals the stored one (so no transition is appended).
     * {@code watermarkNull} true = first sync / repair; {@code transitionsExist} tells whether the
     * item already has transitions (the repair case).
     */
    private andromeda.sei.ms.feature.azure.application.dto.AzureWorkItemsSyncResult syncActivity(
            boolean watermarkNull, boolean transitionsExist, java.util.OptionalInt storedMax,
            List<AdoWorkItemUpdateDto> updates) {
        Instant lastSync = NOW.minusSeconds(3600);
        if (!watermarkNull) binding.setWorkItemsWatermark(lastSync);
        when(azureDevOpsClient.queryWorkItemIdsPage(anyString(), anyString(), anyString(),
                watermarkNull ? isNull() : eq(lastSync), isNull(), any())).thenReturn(List.of(100));
        when(azureDevOpsClient.getWorkItemsBatch(anyString(), any(), anyString()))
                .thenReturn(List.of(adoItem(100, "Active")));
        WorkItem stored = workItem("100", "Active");
        when(workItemRepository.findByOrganizationIdAndExternalIdAndSource(anyString(), anyString(), any()))
                .thenReturn(transitionsExist ? Optional.of(stored) : Optional.empty());
        when(workItemRepository.save(any())).thenReturn(stored);
        when(workItemTransitionRepository.existsByWorkItemId(any())).thenReturn(transitionsExist);
        when(workItemTransitionRepository.findAllByWorkItemId(any())).thenReturn(List.of());
        org.mockito.Mockito.lenient().when(workItemTransitionRepository.upsertByCompositeKey(any()))
                .thenAnswer(inv -> inv.getArgument(0));
        if (updates == null) {
            when(azureDevOpsClient.getWorkItemUpdates(anyString(), eq(100), anyString()))
                    .thenThrow(new RuntimeException("updates down"));
        } else {
            when(azureDevOpsClient.getWorkItemUpdates(anyString(), eq(100), anyString())).thenReturn(updates);
        }
        when(workItemActivityRepository.maxRevisionByWorkItemId("org-1", "wi-100")).thenReturn(storedMax);
        org.mockito.Mockito.lenient().when(workItemActivityRepository.upsert(any()))
                .thenAnswer(inv -> inv.getArgument(0));
        return useCase.sync(connection, binding, SyncTrigger.SCHEDULED, null);
    }

    private void authorsResolve() {
        var userA = userOf("u-a", "org-1");
        var userB = userOf("u-b", "org-1");
        org.mockito.Mockito.lenient().when(userRepository.findByOrganizationIdAndAzureId("org-1", "az-1")).thenReturn(Optional.of(userA));
        org.mockito.Mockito.lenient().when(userRepository.findByOrganizationIdAndAzureId("org-1", "az-2")).thenReturn(Optional.empty());
        org.mockito.Mockito.lenient().when(userRepository.findAllByOrganizationIdAndEmailIgnoringCase("org-1", "b@x.com"))
                .thenReturn(List.of(userB));
    }

    private List<andromeda.sei.ms.feature.workitem.domain.WorkItemActivity> storedActivities() {
        ArgumentCaptor<andromeda.sei.ms.feature.workitem.domain.WorkItemActivity> captor =
                ArgumentCaptor.forClass(andromeda.sei.ms.feature.workitem.domain.WorkItemActivity.class);
        verify(workItemActivityRepository, org.mockito.Mockito.atLeast(0)).upsert(captor.capture());
        return captor.getAllValues();
    }

    @Test
    void shouldStoreAllFiveRevisionsWithAuthorDayAndFieldsOnFirstSync() {
        authorsResolve();

        var result = syncActivity(true, false, java.util.OptionalInt.empty(), revisions(1, 5));

        var stored = storedActivities();
        assertThat(stored).extracting(a -> a.getRevision()).containsExactly(1, 2, 3, 4, 5);
        var first = stored.get(0);
        assertThat(first.getOrganizationId()).isEqualTo("org-1");
        assertThat(first.getWorkItemId()).isEqualTo("wi-100");
        assertThat(first.getExternalId()).isEqualTo("100");
        assertThat(first.getActorExternalId()).isEqualTo("az-1");
        assertThat(first.getActorUniqueName()).isEqualTo("a@x.com");
        assertThat(first.getActorUserId()).isEqualTo("u-a");
        assertThat(first.getChangedAt()).isEqualTo(Instant.parse("2026-10-01T10:00:00Z"));
        assertThat(first.getDay()).isEqualTo(java.time.LocalDate.of(2026, 10, 1));
        assertThat(first.getChangedFields()).containsExactlyInAnyOrder("System.ChangedDate", "System.History");
        assertThat(stored.get(1).getActorUserId()).isEqualTo("u-b");
        // exposed for the allocation refresh: one pair per (user, day) of the NEW activity
        assertThat(result.newActivityDays()).hasSize(5).contains(
                new andromeda.sei.ms.feature.azure.application.dto.ActivityDay("u-a", java.time.LocalDate.of(2026, 10, 1)),
                new andromeda.sei.ms.feature.azure.application.dto.ActivityDay("u-b", java.time.LocalDate.of(2026, 10, 2)));
    }

    @Test
    void shouldNotDuplicateWhenSyncedAgainWithoutNewRevisions() {
        var result = syncActivity(false, true, java.util.OptionalInt.of(5), revisions(1, 5));

        verify(workItemActivityRepository, never()).upsert(any());
        assertThat(result.newActivityDays()).isEmpty();
    }

    @Test
    void shouldStoreOnlyRevisionsAboveTheHighestStoredOnIncrementalSync() {
        authorsResolve();

        var result = syncActivity(false, true, java.util.OptionalInt.of(7), revisions(1, 9));

        assertThat(storedActivities()).extracting(a -> a.getRevision()).containsExactly(8, 9);
        verify(workItemTransitionRepository, never()).upsertByCompositeKey(any());
        assertThat(result.newActivityDays()).hasSize(2);
    }

    @Test
    void shouldKeepTheItemSyncedWhenTheIncrementalUpdatesFetchFails() {
        var result = syncActivity(false, true, java.util.OptionalInt.of(7), null);

        assertThat(result.status()).isEqualTo(SyncStatus.SUCCESS);
        verify(workItemActivityRepository, never()).upsert(any());
        assertThat(result.newActivityDays()).isEmpty();
    }

    @Test
    void shouldBackfillActivityForAnItemThatAlreadyHasTransitions() {
        // the repair path: watermark reset, transitions already exist, no activity stored yet
        authorsResolve();

        syncActivity(true, true, java.util.OptionalInt.empty(), revisions(1, 3));

        assertThat(storedActivities()).extracting(a -> a.getRevision()).containsExactly(1, 2, 3);
        verify(workItemTransitionRepository, never()).upsertByCompositeKey(any());
    }

    @Test
    void shouldMatchRevisionAuthorByEmailWhenAzureIdHasNoUser() {
        var nati = userOf("u-nati", "org-1");
        when(userRepository.findByOrganizationIdAndAzureId("org-1", "az-nati")).thenReturn(Optional.empty());
        when(userRepository.findAllByOrganizationIdAndEmailIgnoringCase("org-1", "nati@empresa.com"))
                .thenReturn(List.of(nati));

        syncActivity(true, false, java.util.OptionalInt.empty(),
                List.of(revision(1, "az-nati", "nati@empresa.com", "2026-10-02T10:00:00Z")));

        assertThat(storedActivities()).singleElement()
                .satisfies(a -> assertThat(a.getActorUserId()).isEqualTo("u-nati"));
    }

    @Test
    void shouldStoreUnknownAuthorWithNullUserAndKeepItOutOfTheNewPairs() {
        when(userRepository.findByOrganizationIdAndAzureId("org-1", "az-svc")).thenReturn(Optional.empty());
        when(userRepository.findAllByOrganizationIdAndEmailIgnoringCase("org-1", "svc@build")).thenReturn(List.of());

        var result = syncActivity(true, false, java.util.OptionalInt.empty(),
                List.of(revision(1, "az-svc", "svc@build", "2026-10-02T10:00:00Z")));

        assertThat(storedActivities()).singleElement().satisfies(a -> {
            assertThat(a.getActorUserId()).isNull();
            assertThat(a.getActorExternalId()).isEqualTo("az-svc");
        });
        assertThat(result.newActivityDays()).isEmpty();
    }

    @Test
    void shouldPutBypassRulesRevisionOnItsRetroactiveChangeDate() {
        // authorized "today" but System.ChangedDate says 2026-09-29
        authorsResolve();

        syncActivity(true, false, java.util.OptionalInt.empty(),
                List.of(revision(1, "az-1", "a@x.com", "2026-09-29T23:30:00Z", "System.State")));

        assertThat(storedActivities()).singleElement().satisfies(a -> {
            assertThat(a.getDay()).isEqualTo(java.time.LocalDate.of(2026, 9, 29));
            assertThat(a.getChangedAt()).isEqualTo(Instant.parse("2026-09-29T23:30:00Z"));
        });
    }
    private IntegrationSyncLog syncTypedItemsAndCaptureLog(List<String> rawTypes) {
        var config = new andromeda.sei.ms.feature.azure.domain.WorkflowConfig(
                List.of(), List.of(new andromeda.sei.ms.feature.azure.domain.TypeMapping("Bug", WorkItem.ResolvedType.BUG)),
                List.of(), null, andromeda.sei.ms.feature.azure.domain.WorkflowConfig.ConfigSource.CUSTOM);
        binding.setWorkflowConfig(config);
        List<Integer> ids = new java.util.ArrayList<>();
        List<AdoWorkItemDto> items = new java.util.ArrayList<>();
        for (int i = 0; i < rawTypes.size(); i++) {
            ids.add(200 + i);
            items.add(new AdoWorkItemDto(200 + i, Map.of(
                    "System.Title", "T" + i,
                    "System.WorkItemType", rawTypes.get(i),
                    "System.State", "Active",
                    "System.CreatedDate", NOW.toString(),
                    "System.ChangedDate", NOW.toString()), null));
        }
        when(azureDevOpsClient.queryWorkItemIdsPage(anyString(), anyString(), anyString(), isNull(), isNull(), any()))
                .thenReturn(ids);
        when(azureDevOpsClient.getWorkItemsBatch(anyString(), any(), anyString())).thenReturn(items);
        when(workItemRepository.findByOrganizationIdAndExternalIdAndSource(anyString(), anyString(), any()))
                .thenReturn(Optional.empty());
        when(workItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        org.mockito.Mockito.lenient().when(workItemTransitionRepository.existsByWorkItemId(any())).thenReturn(true);
        org.mockito.Mockito.lenient().when(workItemTransitionRepository.findAllByWorkItemId(any())).thenReturn(List.of());

        useCase.sync(connection, binding, SyncTrigger.SCHEDULED, null);

        ArgumentCaptor<WorkItem> saved = ArgumentCaptor.forClass(WorkItem.class);
        verify(workItemRepository, atLeastOnce()).save(saved.capture());
        for (int i = 0; i < rawTypes.size(); i++) {
            String raw = rawTypes.get(i);
            if (!"Bug".equalsIgnoreCase(raw)) {
                assertThat(saved.getAllValues()).filteredOn(w -> raw.equals(w.getRawType()))
                        .allMatch(w -> w.getResolvedType() == WorkItem.ResolvedType.TASK);
            }
        }
        ArgumentCaptor<IntegrationSyncLog> logs = ArgumentCaptor.forClass(IntegrationSyncLog.class);
        verify(syncLogRepository, atLeastOnce()).save(logs.capture());
        return logs.getAllValues().get(logs.getAllValues().size() - 1);
    }

    @Test
    void shouldCountAndNameUnmappedTypesInSyncLogAndKeepThemAsTask() {
        IntegrationSyncLog last = syncTypedItemsAndCaptureLog(
                List.of("Issue", "Risk", "Issue", "Bug", "Issue", "Risk"));

        assertThat(last.getItemsUnmappedType()).isEqualTo(5);
        assertThat(last.getUnmappedTypes()).containsExactly("Issue", "Risk");
    }

    @Test
    void shouldRecordZeroUnmappedWhenEveryTypeIsMapped() {
        IntegrationSyncLog last = syncTypedItemsAndCaptureLog(List.of("Bug", "bug"));

        assertThat(last.getItemsUnmappedType()).isZero();
        assertThat(last.getUnmappedTypes()).isEmpty();
    }
}
