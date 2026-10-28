package andromeda.sei.ms.feature.allocation.application.usecase;

import andromeda.sei.ms.feature.aiusage.domain.AIUsage;
import andromeda.sei.ms.feature.aiusage.domain.AIUsageRepository;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocationRepository;
import andromeda.sei.ms.feature.workitem.domain.Sprint;
import andromeda.sei.ms.feature.workitem.domain.SprintRepository;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.allocation.domain.AllocationSplit;
import andromeda.sei.ms.feature.workitem.domain.WorkItemActivity;
import andromeda.sei.ms.feature.workitem.domain.WorkItemActivityRepository;
import andromeda.sei.ms.feature.workitem.domain.WorkItemRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AutoAssignAllocationUseCaseTest {

    @Mock AiUsageAllocationRepository allocationRepository;
    @Mock AIUsageRepository aiUsageRepository;
    @Mock SprintRepository sprintRepository;
    @Mock WorkItemRepository workItemRepository;
    @Mock WorkItemActivityRepository activityRepository;
    @Mock andromeda.sei.ms.feature.azure.domain.AzureProjectBindingRepository bindingRepository;

    AutoAssignAllocationUseCase useCase;

    static final String ORG = "org-1";
    static final String USER = "user-1";
    static final LocalDate DAY = LocalDate.of(2026, 6, 3);

    @BeforeEach
    void setUp() {
        useCase = new AutoAssignAllocationUseCase(
                allocationRepository, aiUsageRepository, sprintRepository, workItemRepository, activityRepository, new andromeda.sei.ms.feature.workitem.application.usecase.EffortBearingLevelResolver(),
                new andromeda.sei.ms.feature.allocation.application.AllocationTypeEligibility(bindingRepository));
    }

    // â”€â”€ Happy path â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    void should_createPendingAllocation_without_stampingUsageRecords_when_unallocatedRecordsAndSprintItemsExist() {
        AIUsage usage1 = aiUsage("usage-1");
        AIUsage usage2 = aiUsage("usage-2");
        when(aiUsageRepository.findUnallocatedByOrganizationIdAndUserIdAndDay(ORG, USER, DAY))
                .thenReturn(List.of(usage1, usage2));

        when(allocationRepository.findByOrgUserAndDay(ORG, USER, DAY)).thenReturn(List.of());

        Sprint sprint = sprint("sprint-1");
        when(sprintRepository.findByOrganizationIdAndDay(ORG, DAY)).thenReturn(List.of(sprint));

        WorkItem item1 = workItem("wi-1", "Fix login bug");
        WorkItem item2 = workItem("wi-2", "Add dashboard");
        when(workItemRepository.findByOrganizationIdAndSprintIdAndAssigneeUserIdAndResolvedStatus(
                ORG, "sprint-1", USER, WorkItem.ResolvedStatus.IN_PROGRESS))
                .thenReturn(List.of(item1, item2));

        AiUsageAllocation savedAllocation = savedAllocation("alloc-1", List.of("usage-1", "usage-2"));
        when(allocationRepository.save(any())).thenReturn(savedAllocation);

        useCase.execute(ORG, USER, DAY);

        ArgumentCaptor<AiUsageAllocation> captor = ArgumentCaptor.forClass(AiUsageAllocation.class);
        verify(allocationRepository).save(captor.capture());

        AiUsageAllocation created = captor.getValue();
        assertThat(created.getAiUsageIds()).containsExactlyInAnyOrder("usage-1", "usage-2");
        assertThat(created.getSplits()).hasSize(2);
        assertThat(created.getStatus()).isEqualTo(AiUsageAllocation.Status.PENDING_VALIDATION);
        assertThat(created.getAssignedBy()).isEqualTo(AiUsageAllocation.AssignedBy.AUTO);

        BigDecimal expectedSplit = new BigDecimal("0.5000");
        created.getSplits().forEach(s -> assertThat(s.getPercentage()).isEqualByComparingTo(expectedSplit));

        // PENDING_VALIDATION must NOT stamp allocationId on AIUsage records
        verify(allocationRepository, never()).updateAllocationIdOnUsageRecords(anyList(), anyString());
    }

    // â”€â”€ Idempotency: all records already allocated â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    void should_returnEarly_when_noUnallocatedRecordsExist() {
        when(aiUsageRepository.findUnallocatedByOrganizationIdAndUserIdAndDay(ORG, USER, DAY))
                .thenReturn(List.of());

        useCase.execute(ORG, USER, DAY);

        verify(allocationRepository, never()).save(any());
        verify(allocationRepository, never()).updateAllocationIdOnUsageRecords(anyList(), anyString());
        verify(sprintRepository, never()).findByOrganizationIdAndDay(anyString(), any());
    }

    // â”€â”€ Idempotency: same PENDING allocation already covers these records â”€â”€â”€â”€â”€

    @Test
    void should_returnEarly_when_identicalPendingAllocationAlreadyExists() {
        AIUsage usage1 = aiUsage("usage-1");
        when(aiUsageRepository.findUnallocatedByOrganizationIdAndUserIdAndDay(ORG, USER, DAY))
                .thenReturn(List.of(usage1));

        AiUsageAllocation existingPending = savedAllocation("alloc-existing", List.of("usage-1"));
        when(allocationRepository.findByOrgUserAndDay(ORG, USER, DAY))
                .thenReturn(List.of(existingPending));

        useCase.execute(ORG, USER, DAY);

        verify(allocationRepository, never()).deleteById(anyString());
        verify(allocationRepository, never()).save(any());
        verify(allocationRepository, never()).updateAllocationIdOnUsageRecords(anyList(), anyString());
    }

    // â”€â”€ No sprint items found â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    void should_leaveRecordsUnallocated_when_noSprintItemsFound() {
        AIUsage usage1 = aiUsage("usage-1");
        when(aiUsageRepository.findUnallocatedByOrganizationIdAndUserIdAndDay(ORG, USER, DAY))
                .thenReturn(List.of(usage1));

        when(allocationRepository.findByOrgUserAndDay(ORG, USER, DAY)).thenReturn(List.of());

        Sprint sprint = sprint("sprint-1");
        when(sprintRepository.findByOrganizationIdAndDay(ORG, DAY)).thenReturn(List.of(sprint));
        when(workItemRepository.findByOrganizationIdAndSprintIdAndAssigneeUserIdAndResolvedStatus(
                ORG, "sprint-1", USER, WorkItem.ResolvedStatus.IN_PROGRESS))
                .thenReturn(List.of());

        useCase.execute(ORG, USER, DAY);

        verify(allocationRepository, never()).save(any());
        verify(allocationRepository, never()).updateAllocationIdOnUsageRecords(anyList(), anyString());
    }

    // â”€â”€ Stale PENDING cleanup â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    void should_deleteStaleAndCreateNew_when_pendingAllocationCoversDifferentRecords() {
        AIUsage usage1 = aiUsage("usage-1");
        AIUsage usage2 = aiUsage("usage-2");
        when(aiUsageRepository.findUnallocatedByOrganizationIdAndUserIdAndDay(ORG, USER, DAY))
                .thenReturn(List.of(usage1, usage2));

        // Stale PENDING covers only usage-1, not usage-2
        AiUsageAllocation stalePending = savedAllocation("alloc-stale", List.of("usage-1"));
        when(allocationRepository.findByOrgUserAndDay(ORG, USER, DAY))
                .thenReturn(List.of(stalePending));

        Sprint sprint = sprint("sprint-1");
        when(sprintRepository.findByOrganizationIdAndDay(ORG, DAY)).thenReturn(List.of(sprint));

        WorkItem item = workItem("wi-1", "Fix login bug");
        when(workItemRepository.findByOrganizationIdAndSprintIdAndAssigneeUserIdAndResolvedStatus(
                ORG, "sprint-1", USER, WorkItem.ResolvedStatus.IN_PROGRESS))
                .thenReturn(List.of(item));

        AiUsageAllocation newAllocation = savedAllocation("alloc-new", List.of("usage-1", "usage-2"));
        when(allocationRepository.save(any())).thenReturn(newAllocation);

        useCase.execute(ORG, USER, DAY);

        verify(allocationRepository).deleteById("alloc-stale");
        verify(allocationRepository).save(any());
        // PENDING_VALIDATION must NOT stamp allocationId on AIUsage records
        verify(allocationRepository, never()).updateAllocationIdOnUsageRecords(anyList(), anyString());
    }

    @Test
    void should_includeFeatureTypedWorkItem_inAllocationSplits_notDropIt() {
        AIUsage usage1 = aiUsage("usage-1");
        when(aiUsageRepository.findUnallocatedByOrganizationIdAndUserIdAndDay(ORG, USER, DAY))
                .thenReturn(List.of(usage1));

        when(allocationRepository.findByOrgUserAndDay(ORG, USER, DAY)).thenReturn(List.of());

        Sprint sprint = sprint("sprint-1");
        when(sprintRepository.findByOrganizationIdAndDay(ORG, DAY)).thenReturn(List.of(sprint));

        WorkItem feature = featureWorkItem("wi-feat", "Ship new capability");
        when(workItemRepository.findByOrganizationIdAndSprintIdAndAssigneeUserIdAndResolvedStatus(
                ORG, "sprint-1", USER, WorkItem.ResolvedStatus.IN_PROGRESS))
                .thenReturn(List.of(feature));

        AiUsageAllocation savedAllocation = savedAllocation("alloc-1", List.of("usage-1"));
        when(allocationRepository.save(any())).thenReturn(savedAllocation);

        useCase.execute(ORG, USER, DAY);

        ArgumentCaptor<AiUsageAllocation> captor = ArgumentCaptor.forClass(AiUsageAllocation.class);
        verify(allocationRepository).save(captor.capture());
        assertThat(captor.getValue().getSplits()).hasSize(1);
        assertThat(captor.getValue().getSplits().get(0).getWorkItemId()).isEqualTo("wi-feat");
    }

    // â”€â”€ Helpers â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    // ── Activity-based suggestion (alocacao-no-dashboard D3) ───────────────

    @Test
    void should_splitEvenlyAcrossActivityItems_lastClosesSum_when_threeItemsChanged() {
        givenUnallocatedUsageAndNoAllocation();
        givenActivityOn("wi-1", "wi-2", "wi-3");
        when(allocationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        useCase.execute(ORG, USER, DAY);

        AiUsageAllocation created = savedOne();
        assertThat(created.getSplits()).extracting(AllocationSplit::getWorkItemId)
                .containsExactly("wi-1", "wi-2", "wi-3");
        assertThat(created.getSplits()).extracting(AllocationSplit::getPercentage)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("0.3333"), new BigDecimal("0.3333"), new BigDecimal("0.3334"));
        assertThat(created.getSuggestionSource()).isEqualTo(AiUsageAllocation.SuggestionSource.AZURE_ACTIVITY);
        verify(sprintRepository, never()).findByOrganizationIdAndDay(anyString(), any());
    }

    @Test
    void should_useOnlyActivityItem_when_otherItemsAreInProgress() {
        givenUnallocatedUsageAndNoAllocation();
        givenActivityOn("wi-1");
        when(allocationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        useCase.execute(ORG, USER, DAY);

        AiUsageAllocation created = savedOne();
        assertThat(created.getSplits()).hasSize(1);
        assertThat(created.getSplits().get(0).getWorkItemId()).isEqualTo("wi-1");
        assertThat(created.getSplits().get(0).getPercentage()).isEqualByComparingTo("1.0000");
        assertThat(created.getSuggestionSource()).isEqualTo(AiUsageAllocation.SuggestionSource.AZURE_ACTIVITY);
    }

    @Test
    void should_countRepeatedRevisionsOfTheSameItemOnce() {
        givenUnallocatedUsageAndNoAllocation();
        when(activityRepository.findByOrganizationIdAndActorUserIdAndDay(ORG, USER, DAY))
                .thenReturn(List.of(activity("wi-1", 1), activity("wi-1", 2), activity("wi-2", 3)));
        stubItem("wi-1");
        stubItem("wi-2");
        when(allocationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        useCase.execute(ORG, USER, DAY);

        assertThat(savedOne().getSplits()).extracting(AllocationSplit::getPercentage)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("0.5000"), new BigDecimal("0.5000"));
    }

    @Test
    void should_fallBackToInProgress_withSourceInProgress_when_noActivity() {
        givenUnallocatedUsageAndNoAllocation();
        givenInProgress(workItem("wi-a", "A"), workItem("wi-b", "B"));
        when(allocationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        useCase.execute(ORG, USER, DAY);

        AiUsageAllocation created = savedOne();
        assertThat(created.getSplits()).hasSize(2);
        assertThat(created.getSuggestionSource()).isEqualTo(AiUsageAllocation.SuggestionSource.IN_PROGRESS);
    }

    @Test
    void should_fallBackToInProgress_when_activityItemsAreAllFilteredOut() {
        givenUnallocatedUsageAndNoAllocation();
        when(activityRepository.findByOrganizationIdAndActorUserIdAndDay(ORG, USER, DAY))
                .thenReturn(List.of(activity("wi-removed", 1), activity("wi-unknown", 2)));
        when(workItemRepository.findByIdAndOrganizationIdAndActiveTrue("wi-removed", ORG)).thenReturn(Optional.empty());
        when(workItemRepository.findByIdAndOrganizationIdAndActiveTrue("wi-unknown", ORG)).thenReturn(Optional.empty());
        givenInProgress(workItem("wi-a", "A"));
        when(allocationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        useCase.execute(ORG, USER, DAY);

        assertThat(savedOne().getSuggestionSource()).isEqualTo(AiUsageAllocation.SuggestionSource.IN_PROGRESS);
        assertThat(savedOne().getSplits()).extracting(AllocationSplit::getWorkItemId).containsExactly("wi-a");
    }

    @Test
    void should_createNothing_when_noActivityAndNothingInProgress() {
        givenUnallocatedUsageAndNoAllocation();
        givenInProgress();

        useCase.execute(ORG, USER, DAY);

        verify(allocationRepository, never()).save(any());
    }

    @Test
    void should_leaveTaskOut_when_taskHasNoEffortAndLevelResolvesToStory() {
        givenUnallocatedUsageAndNoAllocation();
        WorkItem task = taskWorkItem("wi-task", null);
        when(activityRepository.findByOrganizationIdAndActorUserIdAndDay(ORG, USER, DAY))
                .thenReturn(List.of(activity("wi-task", 1)));
        when(workItemRepository.findByIdAndOrganizationIdAndActiveTrue("wi-task", ORG)).thenReturn(Optional.of(task));
        givenInProgress();

        useCase.execute(ORG, USER, DAY);

        verify(allocationRepository, never()).save(any());
    }

    @Test
    void should_keepTask_when_taskCarriesEffort() {
        givenUnallocatedUsageAndNoAllocation();
        WorkItem task = taskWorkItem("wi-task", 2.0);
        when(activityRepository.findByOrganizationIdAndActorUserIdAndDay(ORG, USER, DAY))
                .thenReturn(List.of(activity("wi-task", 1)));
        when(workItemRepository.findByIdAndOrganizationIdAndActiveTrue("wi-task", ORG)).thenReturn(Optional.of(task));
        when(allocationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        useCase.execute(ORG, USER, DAY);

        assertThat(savedOne().getSplits()).extracting(AllocationSplit::getWorkItemId).containsExactly("wi-task");
    }

    @Test
    void should_neverTouchValidatedDay() {
        when(aiUsageRepository.findUnallocatedByOrganizationIdAndUserIdAndDay(ORG, USER, DAY))
                .thenReturn(List.of(aiUsage("usage-9")));
        AiUsageAllocation validated = savedAllocation("alloc-v", List.of("usage-1"));
        validated.setStatus(AiUsageAllocation.Status.VALIDATED);
        when(allocationRepository.findByOrgUserAndDay(ORG, USER, DAY)).thenReturn(List.of(validated));

        useCase.execute(ORG, USER, DAY, true);

        verify(allocationRepository, never()).save(any());
        verify(allocationRepository, never()).deleteById(anyString());
    }

    @Test
    void should_recomputeWhenForced_replacingPendingWithNewSource() {
        when(aiUsageRepository.findUnallocatedByOrganizationIdAndUserIdAndDay(ORG, USER, DAY))
                .thenReturn(List.of(aiUsage("usage-1")));
        AiUsageAllocation pending = savedAllocation("alloc-p", List.of("usage-1"));
        pending.setSplits(List.of(new AllocationSplit("wi-old", "Old", new BigDecimal("1.0000"))));
        pending.setSuggestionSource(AiUsageAllocation.SuggestionSource.IN_PROGRESS);
        when(allocationRepository.findByOrgUserAndDay(ORG, USER, DAY)).thenReturn(List.of(pending));
        givenActivityOn("wi-1");
        when(allocationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        useCase.execute(ORG, USER, DAY, true);

        verify(allocationRepository).deleteById("alloc-p");
        assertThat(savedOne().getSuggestionSource()).isEqualTo(AiUsageAllocation.SuggestionSource.AZURE_ACTIVITY);
    }

    @Test
    void should_notRewriteWhenForced_butSuggestionIsUnchanged() {
        when(aiUsageRepository.findUnallocatedByOrganizationIdAndUserIdAndDay(ORG, USER, DAY))
                .thenReturn(List.of(aiUsage("usage-1")));
        AiUsageAllocation pending = savedAllocation("alloc-p", List.of("usage-1"));
        pending.setSplits(List.of(new AllocationSplit("wi-1", "wi-1", new BigDecimal("1.0000"))));
        pending.setSuggestionSource(AiUsageAllocation.SuggestionSource.AZURE_ACTIVITY);
        when(allocationRepository.findByOrgUserAndDay(ORG, USER, DAY)).thenReturn(List.of(pending));
        givenActivityOn("wi-1");

        useCase.execute(ORG, USER, DAY, true);

        verify(allocationRepository, never()).deleteById(anyString());
        verify(allocationRepository, never()).save(any());
    }

    @Test
    void should_notRecomputeWithoutForce_whenSameUsageSet() {
        when(aiUsageRepository.findUnallocatedByOrganizationIdAndUserIdAndDay(ORG, USER, DAY))
                .thenReturn(List.of(aiUsage("usage-1")));
        when(allocationRepository.findByOrgUserAndDay(ORG, USER, DAY))
                .thenReturn(List.of(savedAllocation("alloc-p", List.of("usage-1"))));

        useCase.execute(ORG, USER, DAY);

        verify(activityRepository, never()).findByOrganizationIdAndActorUserIdAndDay(anyString(), anyString(), any());
        verify(allocationRepository, never()).save(any());
    }

    // ── Bulk day (ALO3): hundreds of items touched is no signal ──────────────

    @Test
    void should_fallBackToInProgress_when_activityTouchesMoreThanTenItems() {
        givenUnallocatedUsageAndNoAllocation();
        givenActivityOn(ids("wi-bulk-", 231));
        givenInProgress(workItem("wi-a", "A"), workItem("wi-b", "B"));
        when(allocationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        useCase.execute(ORG, USER, DAY);

        AiUsageAllocation created = savedOne();
        assertThat(created.getSuggestionSource()).isEqualTo(AiUsageAllocation.SuggestionSource.IN_PROGRESS);
        assertThat(created.getSplits()).extracting(AllocationSplit::getWorkItemId).containsExactly("wi-a", "wi-b");
    }

    @Test
    void should_keepActivity_when_exactlyTenItemsChanged() {
        givenUnallocatedUsageAndNoAllocation();
        givenActivityOn(ids("wi-", 10));
        when(allocationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        useCase.execute(ORG, USER, DAY);

        AiUsageAllocation created = savedOne();
        assertThat(created.getSuggestionSource()).isEqualTo(AiUsageAllocation.SuggestionSource.AZURE_ACTIVITY);
        assertThat(created.getSplits()).hasSize(10);
    }

    @Test
    void should_createNothing_when_bothActivityAndInProgressExceedTenItems() {
        givenUnallocatedUsageAndNoAllocation();
        givenActivityOn(ids("wi-bulk-", 231));
        givenInProgress(java.util.Arrays.stream(ids("wi-ip-", 11)).map(id -> workItem(id, id))
                .toArray(WorkItem[]::new));

        useCase.execute(ORG, USER, DAY);

        verify(allocationRepository, never()).save(any());
    }

    @Test
    void should_dropStalePendingBulkSuggestion_when_forcedAndNowNoSuggestion() {
        when(aiUsageRepository.findUnallocatedByOrganizationIdAndUserIdAndDay(ORG, USER, DAY))
                .thenReturn(List.of(aiUsage("usage-1")));
        AiUsageAllocation stale = savedAllocation("alloc-bulk", List.of("usage-1"));
        when(allocationRepository.findByOrgUserAndDay(ORG, USER, DAY)).thenReturn(List.of(stale));
        givenActivityOn(ids("wi-bulk-", 231));
        givenInProgress();

        useCase.execute(ORG, USER, DAY, true);

        verify(allocationRepository).deleteById("alloc-bulk");
        verify(allocationRepository, never()).save(any());
    }

    @Test
    void should_countAnItemInTwoSprintsOfTheDayOnce() {
        givenUnallocatedUsageAndNoAllocation();
        when(sprintRepository.findByOrganizationIdAndDay(ORG, DAY))
                .thenReturn(List.of(sprint("sprint-1"), sprint("sprint-2")));
        WorkItem shared = workItem("wi-a", "A");
        when(workItemRepository.findByOrganizationIdAndSprintIdAndAssigneeUserIdAndResolvedStatus(
                ORG, "sprint-1", USER, WorkItem.ResolvedStatus.IN_PROGRESS)).thenReturn(List.of(shared));
        when(workItemRepository.findByOrganizationIdAndSprintIdAndAssigneeUserIdAndResolvedStatus(
                ORG, "sprint-2", USER, WorkItem.ResolvedStatus.IN_PROGRESS))
                .thenReturn(List.of(workItem("wi-a", "A"), workItem("wi-b", "B")));
        when(allocationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        useCase.execute(ORG, USER, DAY);

        assertThat(savedOne().getSplits()).extracting(AllocationSplit::getWorkItemId).containsExactly("wi-a", "wi-b");
    }

    // ── Peso por horas (dashboards-numeros D4) ──────────────────────────────

    private WorkItem childTask(String id, String parentExternalId, Double effort) {
        WorkItem t = taskWorkItem(id, effort);
        t.setParentExternalId(parentExternalId);
        return t;
    }

    private List<BigDecimal> weightedPercentages(Double h1, Double h2, Double h3) {
        givenUnallocatedUsageAndNoAllocation();
        givenInProgress(workItem("wi-1", "A"), workItem("wi-2", "B"), workItem("wi-3", "C"));
        List<WorkItem> children = new java.util.ArrayList<>();
        if (h1 != null) children.add(childTask("t1", "wi-1", h1));
        if (h3 != null) children.add(childTask("t3", "wi-3", h3));
        if (h2 != null) children.add(childTask("t2", "wi-2", h2));
        when(workItemRepository.findByOrganizationIdAndProjectIdAndParentExternalIdIn(eq(ORG), eq("proj-1"), anyList()))
                .thenAnswer(i -> {
                    List<String> parents = i.getArgument(2);
                    return children.stream().filter(c -> parents.contains(c.getParentExternalId())).toList();
                });
        when(allocationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        useCase.execute(ORG, USER, DAY);

        return savedOne().getSplits().stream().map(AllocationSplit::getPercentage).toList();
    }

    @Test
    void should_weightSplitsByHoursOfChildTasks() {
        assertThat(weightedPercentages(5.0, null, 1.0).stream().map(BigDecimal::toPlainString).toList())
                .isEqualTo(List.of("0.7143", "0.1429", "0.1428"));
    }

    @Test
    void should_giveEightOneOne_whenHoursAre8_none_1() {
        // 8 h / no value (weight 1) / 1 h  =>  8:1:1
        assertThat(weightedPercentages(8.0, null, 1.0).stream().map(BigDecimal::toPlainString).toList())
                .isEqualTo(List.of("0.8000", "0.1000", "0.1000"));
    }

    @Test
    void should_splitEvenly_when_noItemHasHours() {
        assertThat(weightedPercentages(null, null, null).stream().map(BigDecimal::toPlainString).toList())
                .isEqualTo(List.of("0.3333", "0.3333", "0.3334"));
    }

    @Test
    void should_closeAtOne_when_threeItemsHaveOneHourEach() {
        List<BigDecimal> p = weightedPercentages(1.0, 1.0, 1.0);
        assertThat(p.stream().map(BigDecimal::toPlainString).toList()).isEqualTo(List.of("0.3333", "0.3333", "0.3334"));
        assertThat(p.stream().reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("1.0000");
    }

    @Test
    void should_weightHalfHourAsOne() {
        assertThat(weightedPercentages(0.5, 4.0, null).stream().map(BigDecimal::toPlainString).toList())
                .isEqualTo(List.of("0.1667", "0.6667", "0.1666"));
    }

    private static String[] ids(String prefix, int n) {
        return java.util.stream.IntStream.rangeClosed(1, n).mapToObj(i -> prefix + i).toArray(String[]::new);
    }

    private void givenUnallocatedUsageAndNoAllocation() {
        when(aiUsageRepository.findUnallocatedByOrganizationIdAndUserIdAndDay(ORG, USER, DAY))
                .thenReturn(List.of(aiUsage("usage-1")));
        when(allocationRepository.findByOrgUserAndDay(ORG, USER, DAY)).thenReturn(List.of());
    }

    private void givenActivityOn(String... workItemIds) {
        int rev = 1;
        List<WorkItemActivity> acts = new java.util.ArrayList<>();
        for (String id : workItemIds) {
            acts.add(activity(id, rev++));
            stubItem(id);
        }
        when(activityRepository.findByOrganizationIdAndActorUserIdAndDay(ORG, USER, DAY)).thenReturn(acts);
    }

    private void stubItem(String id) {
        when(workItemRepository.findByIdAndOrganizationIdAndActiveTrue(id, ORG))
                .thenReturn(Optional.of(workItem(id, id)));
    }

    private void givenInProgress(WorkItem... items) {
        when(sprintRepository.findByOrganizationIdAndDay(ORG, DAY)).thenReturn(List.of(sprint("sprint-1")));
        when(workItemRepository.findByOrganizationIdAndSprintIdAndAssigneeUserIdAndResolvedStatus(
                ORG, "sprint-1", USER, WorkItem.ResolvedStatus.IN_PROGRESS)).thenReturn(List.of(items));
    }

    private AiUsageAllocation savedOne() {
        ArgumentCaptor<AiUsageAllocation> captor = ArgumentCaptor.forClass(AiUsageAllocation.class);
        verify(allocationRepository).save(captor.capture());
        return captor.getValue();
    }

    private WorkItemActivity activity(String workItemId, int revision) {
        Instant at = Instant.parse("2026-06-03T10:00:00Z").plusSeconds(revision);
        return new WorkItemActivity(null, ORG, workItemId, "ext-" + workItemId, revision, "az-1", "u@x", USER,
                at, DAY, List.of("System.State"), true, 0L, at, at);
    }

    private WorkItem taskWorkItem(String id, Double effort) {
        return new WorkItem(id, ORG, "proj-1", WorkItem.Source.AZURE_DEVOPS,
                id, Instant.now(), "Task " + id,
                "Task", WorkItem.ResolvedType.TASK,
                "Active", WorkItem.ResolvedStatus.IN_PROGRESS,
                "Medium", List.of(),
                "sprint-1", "ext-user", USER, null, null,
                Instant.now(), Instant.now(), null,
                null, null, true, 1L, Instant.now(), null, null, null, null, effort, null, null, null);
    }

    @Test
    void should_ignoreInactiveWorkItems_when_assigning() {
        when(aiUsageRepository.findUnallocatedByOrganizationIdAndUserIdAndDay(ORG, USER, DAY))
                .thenReturn(List.of(aiUsage("usage-1")));
        when(allocationRepository.findByOrgUserAndDay(ORG, USER, DAY)).thenReturn(List.of());
        when(sprintRepository.findByOrganizationIdAndDay(ORG, DAY)).thenReturn(List.of(sprint("sprint-1")));
        WorkItem active = workItem("wi-active", "Ativo");
        WorkItem removed = workItem("wi-removed", "Excluido");
        removed.setActive(false);
        when(workItemRepository.findByOrganizationIdAndSprintIdAndAssigneeUserIdAndResolvedStatus(
                ORG, "sprint-1", USER, WorkItem.ResolvedStatus.IN_PROGRESS))
                .thenReturn(List.of(active, removed));
        when(allocationRepository.save(any())).thenReturn(savedAllocation("alloc-1", List.of("usage-1")));

        useCase.execute(ORG, USER, DAY);

        ArgumentCaptor<AiUsageAllocation> captor = ArgumentCaptor.forClass(AiUsageAllocation.class);
        verify(allocationRepository).save(captor.capture());
        assertThat(captor.getValue().getSplits()).extracting(sp -> sp.getWorkItemId())
                .containsExactly("wi-active");
    }

    private AIUsage aiUsage(String id) {
        return new AIUsage(
                id, ORG, USER, "gh-login", "dataset-1", DAY, "gpt-4o",
                "ws-1", "Workspace 1", "mc-1", "Machine 1",
                1000L, 500L, 10, "vscode", null,
                new BigDecimal("0.05"), Instant.now(), true, null, Instant.now(), Instant.now()
        );
    }

    private AiUsageAllocation savedAllocation(String id, List<String> aiUsageIds) {
        return new AiUsageAllocation(
                id, ORG, USER, DAY,
                aiUsageIds, List.of(),
                AiUsageAllocation.Status.PENDING_VALIDATION,
                AiUsageAllocation.AssignedBy.AUTO,
                true, 0L, Instant.now(), Instant.now()
        );
    }

    private Sprint sprint(String id) {
        return new Sprint(id, ORG, "proj-1", WorkItem.Source.AZURE_DEVOPS,
                "ext-1", "team-1", "Team A", "Sprint 23", "/path",
                Instant.now().minusSeconds(86400), Instant.now().plusSeconds(86400),
                Sprint.TimeFrame.CURRENT, true, 1L, Instant.now(), Instant.now());
    }

    private WorkItem workItem(String id, String title) {
        return new WorkItem(id, ORG, "proj-1", WorkItem.Source.AZURE_DEVOPS,
                id, Instant.now(), title,
                "Story", WorkItem.ResolvedType.STORY,
                "Active", WorkItem.ResolvedStatus.IN_PROGRESS,
                "Medium", List.of(),
                "sprint-1", "ext-user", USER, null, null,
                Instant.now(), Instant.now(), null,
                null, null, true, 1L, Instant.now(), null, null, null, null, null, null, null, null);
    }

    private WorkItem featureWorkItem(String id, String title) {
        return new WorkItem(id, ORG, "proj-1", WorkItem.Source.AZURE_DEVOPS,
                id, Instant.now(), title,
                "Feature", WorkItem.ResolvedType.FEATURE,
                "Active", WorkItem.ResolvedStatus.IN_PROGRESS,
                "Medium", List.of(),
                "sprint-1", "ext-user", USER, null, null,
                Instant.now(), Instant.now(), null,
                null, null, true, 1L, Instant.now(), null, null, null, null, null, null, null, null);
    }

    @Test
    void should_splitOnlyAmongMarkedTypes_when_bindingMarksUserStoryAndBug() {
        when(aiUsageRepository.findUnallocatedByOrganizationIdAndUserIdAndDay(ORG, USER, DAY))
                .thenReturn(List.of(aiUsage("usage-1")));
        when(allocationRepository.findByOrgUserAndDay(ORG, USER, DAY)).thenReturn(List.of());
        when(sprintRepository.findByOrganizationIdAndDay(ORG, DAY)).thenReturn(List.of(sprint("sprint-1")));
        List<WorkItem> items = new java.util.ArrayList<>();
        items.add(typedItem("us", "User Story", WorkItem.ResolvedType.STORY));
        items.add(typedItem("bug", "Bug", WorkItem.ResolvedType.BUG));
        items.add(typedItem("feat", "Feature", WorkItem.ResolvedType.FEATURE));
        for (String t : List.of("t1", "t2", "t3")) items.add(typedItem(t, "Task", WorkItem.ResolvedType.TASK));
        when(workItemRepository.findByOrganizationIdAndSprintIdAndAssigneeUserIdAndResolvedStatus(
                ORG, "sprint-1", USER, WorkItem.ResolvedStatus.IN_PROGRESS)).thenReturn(items);
        var mappings = List.of(
                new andromeda.sei.ms.feature.azure.domain.TypeMapping("User Story", WorkItem.ResolvedType.STORY, true),
                new andromeda.sei.ms.feature.azure.domain.TypeMapping("Bug", WorkItem.ResolvedType.BUG, true),
                new andromeda.sei.ms.feature.azure.domain.TypeMapping("Feature", WorkItem.ResolvedType.FEATURE, false));
        var config = org.mockito.Mockito.mock(andromeda.sei.ms.feature.azure.domain.WorkflowConfig.class);
        when(config.getTypeMappings()).thenReturn(mappings);
        var binding = org.mockito.Mockito.mock(andromeda.sei.ms.feature.azure.domain.AzureProjectBinding.class);
        when(binding.getWorkflowConfig()).thenReturn(config);
        when(bindingRepository.findByOrganizationIdAndProjectIdAndActiveTrue(ORG, "proj-1"))
                .thenReturn(java.util.Optional.of(binding));
        when(allocationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(ORG, USER, DAY);

        org.mockito.ArgumentCaptor<AiUsageAllocation> captor = org.mockito.ArgumentCaptor.forClass(AiUsageAllocation.class);
        verify(allocationRepository).save(captor.capture());
        assertThat(captor.getValue().getSplits()).extracting(AllocationSplit::getWorkItemId)
                .containsExactly("us", "bug");
    }

    private WorkItem typedItem(String id, String rawType, WorkItem.ResolvedType type) {
        WorkItem wi = workItem(id, "Title " + id);
        wi.setRawType(rawType);
        wi.setResolvedType(type);
        return wi;
    }
}
