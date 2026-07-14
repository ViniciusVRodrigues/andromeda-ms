package andromeda.sei.ms.feature.allocation.application.usecase;

import andromeda.sei.ms.feature.aiusage.domain.AIUsage;
import andromeda.sei.ms.feature.aiusage.domain.AIUsageRepository;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocationRepository;
import andromeda.sei.ms.feature.workitem.domain.Sprint;
import andromeda.sei.ms.feature.workitem.domain.SprintRepository;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.domain.WorkItemRepository;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AutoAssignAllocationUseCaseTest {

    @Mock AiUsageAllocationRepository allocationRepository;
    @Mock AIUsageRepository aiUsageRepository;
    @Mock SprintRepository sprintRepository;
    @Mock WorkItemRepository workItemRepository;

    AutoAssignAllocationUseCase useCase;

    static final String ORG = "org-1";
    static final String USER = "user-1";
    static final LocalDate DAY = LocalDate.of(2026, 6, 3);

    @BeforeEach
    void setUp() {
        useCase = new AutoAssignAllocationUseCase(
                allocationRepository, aiUsageRepository, sprintRepository, workItemRepository);
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
                "Medium", 3.0, List.of(),
                "sprint-1", "ext-user", USER, null, null,
                Instant.now(), Instant.now(), null,
                null, null, null, true, 1L, Instant.now(), null, null, null, null, null, null, null, null);
    }

    private WorkItem featureWorkItem(String id, String title) {
        return new WorkItem(id, ORG, "proj-1", WorkItem.Source.AZURE_DEVOPS,
                id, Instant.now(), title,
                "Feature", WorkItem.ResolvedType.FEATURE,
                "Active", WorkItem.ResolvedStatus.IN_PROGRESS,
                "Medium", 3.0, List.of(),
                "sprint-1", "ext-user", USER, null, null,
                Instant.now(), Instant.now(), null,
                null, null, null, true, 1L, Instant.now(), null, null, null, null, null, null, null, null);
    }
}
