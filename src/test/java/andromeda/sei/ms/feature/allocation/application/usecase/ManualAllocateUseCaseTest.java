package andromeda.sei.ms.feature.allocation.application.usecase;

import andromeda.sei.ms.feature.aiusage.domain.AIUsage;
import andromeda.sei.ms.feature.aiusage.domain.AIUsageRepository;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocationRepository;
import andromeda.sei.ms.feature.allocation.domain.AllocationSplit;
import andromeda.sei.ms.feature.allocation.domain.exception.AllocationAlreadyValidatedException;
import andromeda.sei.ms.feature.allocation.domain.exception.AllocationNothingToAllocateException;
import andromeda.sei.ms.feature.allocation.domain.exception.AllocationSplitsInvalidException;
import andromeda.sei.ms.feature.workitem.application.usecase.CalculateAiRatiosUseCase;
import andromeda.sei.ms.feature.workitem.domain.WorkItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ManualAllocateUseCaseTest {

    @Mock
    private AiUsageAllocationRepository allocationRepository;

    @Mock
    private AIUsageRepository aiUsageRepository;

    @Mock
    private WorkItemRepository workItemRepository;

    @Mock
    private CalculateAiRatiosUseCase calculateAiRatiosUseCase;

    @Mock private andromeda.sei.ms.feature.allocation.application.AllocationTypeEligibility typeEligibility;

    @Mock
    private andromeda.sei.ms.feature.aiusage.domain.AgentUsageQueries agentUsageQueries;

    @InjectMocks
    private ManualAllocateUseCase useCase;

    private static final String ORG_ID = "org-1";
    private static final String USER_ID = "user-1";
    private static final String ALLOCATION_ID = "alloc-1";
    private static final LocalDate DAY = LocalDate.of(2025, 6, 1);

    private List<AllocationSplit> validSplits;
    private AiUsageAllocation pendingAllocation;

    @BeforeEach
    void setUp() {
        validSplits = List.of(
                new AllocationSplit("wi-1", "Feature A", new BigDecimal("0.6000")),
                new AllocationSplit("wi-2", "Feature B", new BigDecimal("0.4000")));

        org.mockito.Mockito.lenient().when(typeEligibility.isEligible(any(), any())).thenReturn(true);
        org.mockito.Mockito.lenient().when(workItemRepository.findByIdAndOrganizationIdAndActiveTrue("wi-1", ORG_ID))
                .thenReturn(Optional.of(workItem("wi-1", "proj-1")));
        org.mockito.Mockito.lenient().when(workItemRepository.findByIdAndOrganizationIdAndActiveTrue("wi-2", ORG_ID))
                .thenReturn(Optional.of(workItem("wi-2", "proj-1")));

        Instant now = Instant.now();
        pendingAllocation = new AiUsageAllocation(
                ALLOCATION_ID, ORG_ID, USER_ID, DAY,
                List.of("usage-1"), List.of(),
                AiUsageAllocation.Status.PENDING_VALIDATION, AiUsageAllocation.AssignedBy.AUTO,
                true, 0L, now, now);
    }

    @Test
    void shouldAllocateManually_whenAllocationIdProvided_replacingThePendingOfTheDay() {
        when(allocationRepository.findById(ALLOCATION_ID, ORG_ID)).thenReturn(Optional.of(pendingAllocation));
        when(allocationRepository.findByOrgUserAndDay(ORG_ID, USER_ID, DAY)).thenReturn(List.of(pendingAllocation));
        when(allocationRepository.save(any())).thenAnswer(i -> {
            AiUsageAllocation a = i.getArgument(0);
            a.setId("new-id");
            return a;
        });

        useCase.execute(ORG_ID, USER_ID, ALLOCATION_ID, null, validSplits);

        ArgumentCaptor<AiUsageAllocation> captor = ArgumentCaptor.forClass(AiUsageAllocation.class);
        verify(allocationRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(AiUsageAllocation.Status.VALIDATED);
        assertThat(captor.getValue().getAssignedBy()).isEqualTo(AiUsageAllocation.AssignedBy.MANUAL);
        assertThat(captor.getValue().isMovedFromAgent()).isFalse();
        verify(allocationRepository).deleteById(ALLOCATION_ID);
        verify(allocationRepository).updateAllocationIdOnUsageRecords(eq(List.of("usage-1")), eq("new-id"));
        verify(agentUsageQueries, never()).stampTask(any(), any(), any(), any(), any(), any());
    }

    private AiUsageAllocation agentPending(String id, String wi, List<String> usageIds) {
        AiUsageAllocation a = new AiUsageAllocation(id, ORG_ID, USER_ID, DAY, usageIds,
                List.of(new AllocationSplit(wi, "T", new BigDecimal("1.0000"))),
                AiUsageAllocation.Status.PENDING_VALIDATION, AiUsageAllocation.AssignedBy.AGENT,
                true, 0L, Instant.now(), Instant.now());
        a.setAgent(new AiUsageAllocation.AgentInfo(AiUsageAllocation.ResolvedBy.BRANCH, List.of(), List.of("b")));
        return a;
    }

    @Test
    void should_absorbAgentAndAutoPendingAndUnallocatedRows_andStampManualOnTheAgentRows() {
        AiUsageAllocation agent = agentPending("agent-1", "wi-9", List.of("ua1", "ua2"));
        AiUsageAllocation validatedAgent = agentPending("agent-v", "wi-8", List.of("uv"));
        validatedAgent.setStatus(AiUsageAllocation.Status.VALIDATED);
        when(allocationRepository.findByOrgUserAndDay(ORG_ID, USER_ID, DAY))
                .thenReturn(List.of(agent, pendingAllocation, validatedAgent));
        when(aiUsageRepository.findUnallocatedByOrganizationIdAndUserIdAndDay(ORG_ID, USER_ID, DAY))
                .thenReturn(List.of(mockAiUsage("usage-1"), mockAiUsage("un-1")));
        when(allocationRepository.save(any())).thenAnswer(i -> {
            AiUsageAllocation a = i.getArgument(0);
            if (a.getId() == null) a.setId("new-id");
            return a;
        });

        useCase.execute(ORG_ID, USER_ID, null, DAY, validSplits);

        ArgumentCaptor<AiUsageAllocation> captor = ArgumentCaptor.forClass(AiUsageAllocation.class);
        verify(allocationRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        AiUsageAllocation created = captor.getAllValues().stream().filter(a -> "new-id".equals(a.getId())).findFirst().orElseThrow();
        assertThat(created.getStatus()).isEqualTo(AiUsageAllocation.Status.VALIDATED);
        assertThat(created.getAssignedBy()).isEqualTo(AiUsageAllocation.AssignedBy.MANUAL);
        assertThat(created.isMovedFromAgent()).isTrue();
        assertThat(created.getAiUsageIds()).containsExactlyInAnyOrder("ua1", "ua2", "usage-1", "un-1");
        // the agent pending is deactivated, the automatic one dropped, the confirmed agent one untouched
        assertThat(agent.isActive()).isFalse();
        verify(allocationRepository).deleteById(ALLOCATION_ID);
        assertThat(validatedAgent.isActive()).isTrue();
        assertThat(created.getAiUsageIds()).doesNotContain("uv");
        // the manual correction wins: MANUAL stamped only on the rows that came from the agent
        ArgumentCaptor<java.util.Collection<String>> stamped = ArgumentCaptor.forClass(java.util.Collection.class);
        verify(agentUsageQueries).stampTask(eq(ORG_ID), stamped.capture(), eq("wi-1"), eq("proj-1"), eq("AB#wi-1"), eq("MANUAL"));
        assertThat(stamped.getValue()).containsExactlyInAnyOrder("ua1", "ua2");
    }

    @Test
    void should_absorbTheWholeDay_whenAdjustingByTheIdOfAnAgentPending() {
        AiUsageAllocation agentA = agentPending("agent-a", "wi-9", List.of("ua1"));
        AiUsageAllocation agentB = agentPending("agent-b", "wi-8", List.of("ub1"));
        when(allocationRepository.findById("agent-a", ORG_ID)).thenReturn(Optional.of(agentA));
        when(allocationRepository.findByOrgUserAndDay(ORG_ID, USER_ID, DAY)).thenReturn(List.of(agentA, agentB));
        when(allocationRepository.save(any())).thenAnswer(i -> {
            AiUsageAllocation a = i.getArgument(0);
            if (a.getId() == null) a.setId("new-id");
            return a;
        });

        useCase.execute(ORG_ID, USER_ID, "agent-a", null, validSplits);

        assertThat(agentA.isActive()).isFalse();
        assertThat(agentB.isActive()).isFalse();
        verify(allocationRepository).updateAllocationIdOnUsageRecords(
                org.mockito.ArgumentMatchers.argThat(ids -> ids.size() == 2 && ids.contains("ua1") && ids.contains("ub1")),
                eq("new-id"));
    }

    @Test
    void should_notTouchAnotherUsersAllocationOfTheDay() {
        AiUsageAllocation foreign = agentPending("agent-x", "wi-9", List.of("ux"));
        foreign.setUserId("someone-else");
        when(allocationRepository.findByOrgUserAndDay(ORG_ID, USER_ID, DAY)).thenReturn(List.of(foreign));
        when(aiUsageRepository.findUnallocatedByOrganizationIdAndUserIdAndDay(ORG_ID, USER_ID, DAY))
                .thenReturn(List.of(mockAiUsage("un-1")));
        when(allocationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        useCase.execute(ORG_ID, USER_ID, null, DAY, validSplits);

        assertThat(foreign.isActive()).isTrue();
        verify(allocationRepository).save(org.mockito.ArgumentMatchers.argThat(a -> a.getAiUsageIds().equals(List.of("un-1"))));
    }

    @Test
    void shouldAllocateManually_whenDayProvided() {
        Instant now = Instant.now();
        AIUsage usage = mockAiUsage("usage-2");
        when(aiUsageRepository.findUnallocatedByOrganizationIdAndUserIdAndDay(ORG_ID, USER_ID, DAY))
                .thenReturn(List.of(usage));

        AiUsageAllocation savedAllocation = new AiUsageAllocation(
                "new-alloc-id", ORG_ID, USER_ID, DAY,
                List.of("usage-2"), validSplits,
                AiUsageAllocation.Status.VALIDATED, AiUsageAllocation.AssignedBy.MANUAL,
                true, 0L, now, now);
        when(allocationRepository.save(any())).thenReturn(savedAllocation);

        useCase.execute(ORG_ID, USER_ID, null, DAY, validSplits);

        ArgumentCaptor<AiUsageAllocation> captor = ArgumentCaptor.forClass(AiUsageAllocation.class);
        verify(allocationRepository).save(captor.capture());
        AiUsageAllocation saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(AiUsageAllocation.Status.VALIDATED);
        assertThat(saved.getAssignedBy()).isEqualTo(AiUsageAllocation.AssignedBy.MANUAL);
        assertThat(saved.getAiUsageIds()).containsExactly("usage-2");

        verify(allocationRepository).updateAllocationIdOnUsageRecords(
                eq(List.of("usage-2")), eq("new-alloc-id"));
    }

    @Test
    void shouldThrowWorkItemNotFound_andSaveNothing_whenWorkItemIsFromAnotherOrganization_byAllocationId() {
        when(allocationRepository.findById(ALLOCATION_ID, ORG_ID)).thenReturn(Optional.of(pendingAllocation));
        when(workItemRepository.findByIdAndOrganizationIdAndActiveTrue("wi-foreign", ORG_ID))
                .thenReturn(Optional.empty());
        List<AllocationSplit> splits = List.of(
                new AllocationSplit("wi-1", "A", new BigDecimal("0.5000")),
                new AllocationSplit("wi-foreign", "B", new BigDecimal("0.5000")));

        assertThatThrownBy(() -> useCase.execute(ORG_ID, USER_ID, ALLOCATION_ID, null, splits))
                .isInstanceOf(andromeda.sei.ms.feature.workitem.domain.exception.WorkItemNotFoundException.class);

        verify(allocationRepository, never()).save(any());
        verify(allocationRepository, never()).updateAllocationIdOnUsageRecords(any(), any());
        verify(calculateAiRatiosUseCase, never()).execute(any(), any());
    }

    @Test
    void shouldThrowWorkItemNotFound_andDeleteNothing_whenWorkItemIsFromAnotherOrganization_byDay() {
        when(allocationRepository.findByOrgUserAndDay(ORG_ID, USER_ID, DAY)).thenReturn(List.of(pendingAllocation));
        when(aiUsageRepository.findUnallocatedByOrganizationIdAndUserIdAndDay(ORG_ID, USER_ID, DAY))
                .thenReturn(List.of(mockAiUsage("usage-2")));
        when(workItemRepository.findByIdAndOrganizationIdAndActiveTrue("wi-foreign", ORG_ID))
                .thenReturn(Optional.empty());
        List<AllocationSplit> splits = List.of(new AllocationSplit("wi-foreign", "B", BigDecimal.ONE));

        assertThatThrownBy(() -> useCase.execute(ORG_ID, USER_ID, null, DAY, splits))
                .isInstanceOf(andromeda.sei.ms.feature.workitem.domain.exception.WorkItemNotFoundException.class);

        verify(allocationRepository, never()).deleteById(any());
        verify(allocationRepository, never()).save(any());
    }

    @Test
    void shouldRecalculateAiRatiosOfEveryProjectOfTheSplits() {
        when(allocationRepository.findById(ALLOCATION_ID, ORG_ID)).thenReturn(Optional.of(pendingAllocation));
        when(allocationRepository.findByOrgUserAndDay(ORG_ID, USER_ID, DAY)).thenReturn(List.of(pendingAllocation));
        when(allocationRepository.save(any())).thenReturn(pendingAllocation);
        when(workItemRepository.findByIdAndOrganizationIdAndActiveTrue("wi-2", ORG_ID))
                .thenReturn(Optional.of(workItem("wi-2", "proj-2")));

        useCase.execute(ORG_ID, USER_ID, ALLOCATION_ID, null, validSplits);

        verify(calculateAiRatiosUseCase).execute(ORG_ID, "proj-1");
        verify(calculateAiRatiosUseCase).execute(ORG_ID, "proj-2");
    }

    @Test
    void shouldThrowSplitsInvalid_whenPercentagesSumIsNot1() {
        List<AllocationSplit> invalidSplits = List.of(
                new AllocationSplit("wi-1", "Feature A", new BigDecimal("0.5000")),
                new AllocationSplit("wi-2", "Feature B", new BigDecimal("0.3000")));

        assertThatThrownBy(() -> useCase.execute(ORG_ID, USER_ID, null, DAY, invalidSplits))
                .isInstanceOf(AllocationSplitsInvalidException.class);

        verify(allocationRepository, never()).save(any());
        verify(aiUsageRepository, never()).findUnallocatedByOrganizationIdAndUserIdAndDay(any(), any(), any());
    }

    @Test
    void shouldReject409_whenDayAlreadyValidated() {
        AiUsageAllocation validated = new AiUsageAllocation("v-1", ORG_ID, USER_ID, DAY, List.of("usage-1"),
                validSplits, AiUsageAllocation.Status.VALIDATED, AiUsageAllocation.AssignedBy.MANUAL,
                true, 0L, Instant.now(), Instant.now());
        when(allocationRepository.findByOrgUserAndDay(ORG_ID, USER_ID, DAY)).thenReturn(List.of(validated));

        assertThatThrownBy(() -> useCase.execute(ORG_ID, USER_ID, null, DAY, validSplits))
                .isInstanceOf(AllocationAlreadyValidatedException.class);

        verify(allocationRepository, never()).save(any());
        verify(allocationRepository, never()).deleteById(any());
    }

    @Test
    void shouldReject422_whenDayHasNoUnallocatedUsage() {
        when(allocationRepository.findByOrgUserAndDay(ORG_ID, USER_ID, DAY)).thenReturn(List.of());
        when(aiUsageRepository.findUnallocatedByOrganizationIdAndUserIdAndDay(ORG_ID, USER_ID, DAY))
                .thenReturn(List.of());

        assertThatThrownBy(() -> useCase.execute(ORG_ID, USER_ID, null, DAY, validSplits))
                .isInstanceOf(AllocationNothingToAllocateException.class);

        verify(allocationRepository, never()).save(any());
    }

    @Test
    void shouldReplacePendingOfTheDay_whenAllocatingManuallyByDay() {
        when(allocationRepository.findByOrgUserAndDay(ORG_ID, USER_ID, DAY)).thenReturn(List.of(pendingAllocation));
        when(aiUsageRepository.findUnallocatedByOrganizationIdAndUserIdAndDay(ORG_ID, USER_ID, DAY))
                .thenReturn(List.of(mockAiUsage("usage-1")));
        when(allocationRepository.save(any())).thenAnswer(i -> {
            AiUsageAllocation a = i.getArgument(0);
            a.setId("new-id");
            return a;
        });

        useCase.execute(ORG_ID, USER_ID, null, DAY, validSplits);

        verify(allocationRepository).deleteById(ALLOCATION_ID);
        verify(allocationRepository).updateAllocationIdOnUsageRecords(eq(List.of("usage-1")), eq("new-id"));
    }

    private AIUsage mockAiUsage(String id) {
        Instant now = Instant.now();
        return new AIUsage(id, ORG_ID, USER_ID, null, "ds-1", DAY,
                "gpt-4", "ws-1", "ws-name", "m-1", "m-name",
                1000L, 500L, 5, "vscode", null, BigDecimal.ZERO,
                now, true, 0L, now, now);
    }

    private static andromeda.sei.ms.feature.workitem.domain.WorkItem workItem(String id, String projectId) {
        return new andromeda.sei.ms.feature.workitem.domain.WorkItem(id, "org-1", projectId,
                andromeda.sei.ms.feature.workitem.domain.WorkItem.Source.AZURE_DEVOPS,
                id, Instant.now(), "Item " + id,
                "Task", andromeda.sei.ms.feature.workitem.domain.WorkItem.ResolvedType.TASK,
                "Active", andromeda.sei.ms.feature.workitem.domain.WorkItem.ResolvedStatus.IN_PROGRESS,
                "Medium", List.of(),
                "sprint-1", null, null, null, null,
                Instant.now(), Instant.now(), null,
                null, null, true, 1L, Instant.now(), null, null, null, null, null, null, null, null);
    }

    @Test
    void shouldThrowTypeNotEligible_andSaveNothing_byAllocationId() {
        when(allocationRepository.findById(ALLOCATION_ID, ORG_ID)).thenReturn(Optional.of(pendingAllocation));
        var feature = workItem("wi-feature", "proj-1");
        when(workItemRepository.findByIdAndOrganizationIdAndActiveTrue("wi-feature", ORG_ID))
                .thenReturn(Optional.of(feature));
        when(typeEligibility.isEligible(ORG_ID, feature)).thenReturn(false);
        List<AllocationSplit> splits = List.of(
                new AllocationSplit("wi-1", "A", new BigDecimal("0.5000")),
                new AllocationSplit("wi-feature", "B", new BigDecimal("0.5000")));

        assertThatThrownBy(() -> useCase.execute(ORG_ID, USER_ID, ALLOCATION_ID, null, splits))
                .isInstanceOf(andromeda.sei.ms.feature.allocation.domain.exception.AllocationWorkItemTypeNotEligibleException.class)
                .extracting("code").isEqualTo("ALLOCATION_WORK_ITEM_TYPE_NOT_ELIGIBLE");

        verify(allocationRepository, never()).save(any());
        verify(allocationRepository, never()).updateAllocationIdOnUsageRecords(any(), any());
        verify(calculateAiRatiosUseCase, never()).execute(any(), any());
    }

    @Test
    void shouldThrowTypeNotEligible_andDeleteNothing_byDay() {
        when(allocationRepository.findByOrgUserAndDay(ORG_ID, USER_ID, DAY)).thenReturn(List.of(pendingAllocation));
        when(aiUsageRepository.findUnallocatedByOrganizationIdAndUserIdAndDay(ORG_ID, USER_ID, DAY))
                .thenReturn(List.of(mockAiUsage("usage-2")));
        var feature = workItem("wi-feature", "proj-1");
        when(workItemRepository.findByIdAndOrganizationIdAndActiveTrue("wi-feature", ORG_ID))
                .thenReturn(Optional.of(feature));
        when(typeEligibility.isEligible(ORG_ID, feature)).thenReturn(false);
        List<AllocationSplit> splits = List.of(
                new AllocationSplit("wi-1", "A", new BigDecimal("0.5000")),
                new AllocationSplit("wi-feature", "B", new BigDecimal("0.5000")));

        assertThatThrownBy(() -> useCase.execute(ORG_ID, USER_ID, null, DAY, splits))
                .isInstanceOf(andromeda.sei.ms.feature.allocation.domain.exception.AllocationWorkItemTypeNotEligibleException.class);

        verify(allocationRepository, never()).deleteById(any());
        verify(allocationRepository, never()).save(any());
    }

    @Test
    void should_allocateTheDay_when_onlyAnAgentAllocationIsValidated() {
        AiUsageAllocation agent = new AiUsageAllocation("alloc-agent", ORG_ID, USER_ID, DAY, List.of("ua1"),
                List.of(), AiUsageAllocation.Status.VALIDATED, AiUsageAllocation.AssignedBy.AGENT,
                true, 0L, Instant.now(), Instant.now());
        when(allocationRepository.findByOrgUserAndDay(ORG_ID, USER_ID, DAY)).thenReturn(List.of(agent));
        AIUsage usage = mockAiUsage("usage-9");
        when(aiUsageRepository.findUnallocatedByOrganizationIdAndUserIdAndDay(ORG_ID, USER_ID, DAY))
                .thenReturn(List.of(usage));
        when(allocationRepository.save(any())).thenAnswer(inv -> {
            AiUsageAllocation a = inv.getArgument(0);
            a.setId("alloc-new");
            return a;
        });

        useCase.execute(ORG_ID, USER_ID, null, DAY, validSplits);

        verify(allocationRepository, never()).deleteById("alloc-agent");
        verify(allocationRepository).save(any());
    }

    @Test
    void should_rejectUpdatingAgentAllocationById() {
        AiUsageAllocation agent = new AiUsageAllocation(ALLOCATION_ID, ORG_ID, USER_ID, DAY, List.of("ua1"),
                List.of(), AiUsageAllocation.Status.VALIDATED, AiUsageAllocation.AssignedBy.AGENT,
                true, 0L, Instant.now(), Instant.now());
        when(allocationRepository.findById(ALLOCATION_ID, ORG_ID)).thenReturn(Optional.of(agent));

        assertThatThrownBy(() -> useCase.execute(ORG_ID, USER_ID, ALLOCATION_ID, null, validSplits))
                .isInstanceOf(andromeda.sei.ms.feature.allocation.domain.exception.AllocationAlreadyValidatedException.class);
        verify(allocationRepository, never()).save(any());
    }
}
