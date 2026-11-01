package andromeda.sei.ms.feature.allocation.application.usecase;

import andromeda.sei.ms.feature.agent.application.attribution.AttributionSource;
import andromeda.sei.ms.feature.agent.application.attribution.ResolveAgentTaskUseCase;
import andromeda.sei.ms.feature.agent.application.attribution.ResolvedTask;
import andromeda.sei.ms.feature.agent.application.usage.MoveAgentUsageUseCase;
import andromeda.sei.ms.feature.aiusage.domain.AIUsage;
import andromeda.sei.ms.feature.aiusage.domain.AIUsageRepository;
import andromeda.sei.ms.feature.aiusage.domain.AgentUsageQueries;
import andromeda.sei.ms.feature.allocation.application.AllocationApprovalPolicy.Decision;
import andromeda.sei.ms.feature.allocation.application.AllocationApprovalService;
import andromeda.sei.ms.feature.allocation.application.AllocationTypeEligibility;
import andromeda.sei.ms.feature.allocation.application.dto.AllocationEntryInput;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation.ApprovalReason;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation.ApprovalReasonCode;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocationRepository;
import andromeda.sei.ms.feature.allocation.domain.AllocationSplit;
import andromeda.sei.ms.feature.workitem.application.usecase.CalculateAiRatiosUseCase;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.domain.WorkItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Every path that would validate an allocation asks the approval policy and, when it says so, lands in
 * {@code AWAITING_APPROVAL} with the reasons, the rows stamped with the allocation id and no ratio
 * recalculation (uso-ia-fora-de-entrega, design T4 and 2.2).
 */
class AllocationApprovalPathsTest {

    static final String ORG = "org-1";
    static final String USER = "user-1";
    static final LocalDate DAY = LocalDate.of(2026, 10, 28);
    static final LocalDate DAY2 = LocalDate.of(2026, 10, 29);

    static final ApprovalReason REASON = new ApprovalReason(ApprovalReasonCode.REPO_OTHER_PROJECT,
            "org/ms", "p-1", "Andromeda", "wi-1", "AB#1", null, null, null, null, null, null);
    static final Decision ASKS = new Decision(List.of(REASON), List.of("p-1"));

    AiUsageAllocationRepository repo = mock(AiUsageAllocationRepository.class);
    AIUsageRepository usages = mock(AIUsageRepository.class);
    WorkItemRepository items = mock(WorkItemRepository.class);
    AllocationTypeEligibility eligibility = mock(AllocationTypeEligibility.class);
    CalculateAiRatiosUseCase ratios = mock(CalculateAiRatiosUseCase.class);
    AgentUsageQueries agentQueries = mock(AgentUsageQueries.class);
    AllocationApprovalService approval = mock(AllocationApprovalService.class);

    @BeforeEach
    void setUp() {
        when(repo.save(any())).thenAnswer(inv -> {
            AiUsageAllocation a = inv.getArgument(0);
            if (a.getId() == null) a.setId("alloc-new");
            return a;
        });
        when(eligibility.isEligible(any(), any())).thenReturn(true);
        WorkItem wi = mock(WorkItem.class);
        when(wi.getProjectId()).thenReturn("proj-1");
        when(wi.getId()).thenReturn("wi-1");
        when(items.findByIdAndOrganizationIdAndActiveTrue("wi-1", ORG)).thenReturn(Optional.of(wi));
    }

    private void policyAnswers(Decision decision) {
        when(approval.evaluate(any(), any(), any(), any(), any(), any(Collection.class))).thenReturn(decision);
    }

    private AiUsageAllocation allocation(String id, LocalDate day, AiUsageAllocation.Status status,
                                         AiUsageAllocation.AssignedBy by, String... usageIds) {
        return new AiUsageAllocation(id, ORG, USER, day, new ArrayList<>(List.of(usageIds)),
                List.of(new AllocationSplit("wi-1", "T", new BigDecimal("1.0000"))), status, by, true, 0L,
                Instant.now(), Instant.now());
    }

    private static AIUsage row(String id) {
        AIUsage u = mock(AIUsage.class);
        when(u.getId()).thenReturn(id);
        return u;
    }

    private static final List<AllocationSplit> SPLITS = List.of(new AllocationSplit("wi-1", "T", new BigDecimal("1.0000")));

    // ---- /manual and /{id}/manual ------------------------------------------------------------

    @Test
    void manualById_sendsToApproval_absorbingTheAwaitingOfTheDay_withoutRatios() {
        AiUsageAllocation pending = allocation("a-pend", DAY, AiUsageAllocation.Status.PENDING_VALIDATION,
                AiUsageAllocation.AssignedBy.AUTO, "u1");
        AiUsageAllocation awaiting = allocation("a-wait", DAY, AiUsageAllocation.Status.AWAITING_APPROVAL,
                AiUsageAllocation.AssignedBy.MANUAL, "u2");
        when(repo.findById("a-wait", ORG)).thenReturn(Optional.of(awaiting));
        when(repo.findByOrgUserAndDay(ORG, USER, DAY)).thenReturn(List.of(pending, awaiting));
        AIUsage r3 = row("u3");
        when(usages.findUnallocatedByOrganizationIdAndUserIdAndDay(ORG, USER, DAY)).thenReturn(List.of(r3));
        policyAnswers(ASKS);
        ManualAllocateUseCase useCase = new ManualAllocateUseCase(repo, usages, items, eligibility, ratios,
                agentQueries, approval);

        ManualAllocateUseCase.Result result = useCase.execute(ORG, USER, "a-wait", null, SPLITS);

        assertThat(result.allocationId()).isEqualTo("alloc-new");
        assertThat(result.status()).isEqualTo(AiUsageAllocation.Status.AWAITING_APPROVAL);
        assertThat(result.approval().reasons()).containsExactly(REASON);
        assertThat(result.approval().approverProjectIds()).containsExactly("p-1");
        assertThat(result.approval().requestedAt()).isNotNull();
        // both old allocations dropped, rows stamped with the new id, nothing counted in the ratios
        verify(repo).deleteById("a-pend");
        verify(repo).deleteById("a-wait");
        ArgumentCaptor<List<String>> ids = ArgumentCaptor.forClass(List.class);
        verify(repo).updateAllocationIdOnUsageRecords(ids.capture(), eq("alloc-new"));
        assertThat(ids.getValue()).containsExactlyInAnyOrder("u1", "u2", "u3");
        verify(approval).evaluate(eq(ORG), eq(USER), eq(DAY), anyList(), anyList(),
                org.mockito.ArgumentMatchers.<Collection<String>>argThat(c -> c.containsAll(List.of("a-pend", "a-wait"))));
        verify(ratios, never()).execute(any(), any());
    }

    @Test
    void manualForDay_whenFree_validatesAndRecalculatesRatios() {
        when(repo.findByOrgUserAndDay(ORG, USER, DAY)).thenReturn(List.of());
        AIUsage r1 = row("u1");
        when(usages.findUnallocatedByOrganizationIdAndUserIdAndDay(ORG, USER, DAY)).thenReturn(List.of(r1));
        policyAnswers(Decision.free());
        ManualAllocateUseCase useCase = new ManualAllocateUseCase(repo, usages, items, eligibility, ratios,
                agentQueries, approval);

        ManualAllocateUseCase.Result result = useCase.execute(ORG, USER, null, DAY, SPLITS);

        assertThat(result.status()).isEqualTo(AiUsageAllocation.Status.VALIDATED);
        assertThat(result.approval()).isNull();
        verify(ratios).execute(ORG, "proj-1");
    }

    @Test
    void manualForDay_absorbsAnAwaitingAgentAllocation_deactivatingIt() {
        AiUsageAllocation agentWaiting = allocation("a-agent", DAY, AiUsageAllocation.Status.AWAITING_APPROVAL,
                AiUsageAllocation.AssignedBy.AGENT, "u1");
        agentWaiting.setAgent(new AiUsageAllocation.AgentInfo(AiUsageAllocation.ResolvedBy.BRANCH, List.of(), List.of("b")));
        when(repo.findByOrgUserAndDay(ORG, USER, DAY)).thenReturn(List.of(agentWaiting));
        when(usages.findUnallocatedByOrganizationIdAndUserIdAndDay(ORG, USER, DAY)).thenReturn(List.of());
        policyAnswers(ASKS);
        ManualAllocateUseCase useCase = new ManualAllocateUseCase(repo, usages, items, eligibility, ratios,
                agentQueries, approval);

        ManualAllocateUseCase.Result result = useCase.execute(ORG, USER, null, DAY, SPLITS);

        assertThat(agentWaiting.isActive()).isFalse();
        assertThat(result.status()).isEqualTo(AiUsageAllocation.Status.AWAITING_APPROVAL);
        verify(agentQueries).stampTask(eq(ORG), any(), eq("wi-1"), eq("proj-1"), any(), eq("MANUAL"));
    }

    // ---- /confirm -----------------------------------------------------------------------------

    @Test
    void confirm_putsTheDayThatFallsInThePolicyInAwaitingApproval_andConfirmsTheOther() {
        AiUsageAllocation d1 = allocation("a1", DAY, AiUsageAllocation.Status.PENDING_VALIDATION,
                AiUsageAllocation.AssignedBy.AUTO, "u1");
        AiUsageAllocation d2 = allocation("a2", DAY2, AiUsageAllocation.Status.PENDING_VALIDATION,
                AiUsageAllocation.AssignedBy.AUTO, "u2");
        when(repo.findByOrgUserAndDay(ORG, USER, DAY)).thenReturn(List.of(d1));
        when(repo.findByOrgUserAndDay(ORG, USER, DAY2)).thenReturn(List.of(d2));
        when(approval.evaluate(eq(ORG), eq(USER), eq(DAY), any(), any(), any(Collection.class))).thenReturn(ASKS);
        when(approval.evaluate(eq(ORG), eq(USER), eq(DAY2), any(), any(), any(Collection.class)))
                .thenReturn(Decision.free());
        ConfirmAllocationDaysUseCase useCase = new ConfirmAllocationDaysUseCase(repo, items, ratios, approval);

        ConfirmAllocationDaysUseCase.Result result = useCase.execute(ORG, USER, List.of(DAY, DAY2), false);

        assertThat(result.confirmed()).containsExactly(DAY2);
        assertThat(result.awaitingApproval()).containsExactly(DAY);
        assertThat(result.skipped()).isEmpty();
        assertThat(d1.getStatus()).isEqualTo(AiUsageAllocation.Status.AWAITING_APPROVAL);
        assertThat(d1.getApproval().reasons()).containsExactly(REASON);
        assertThat(d2.getStatus()).isEqualTo(AiUsageAllocation.Status.VALIDATED);
        // the awaiting day keeps its rows carrying the allocation id
        verify(repo).updateAllocationIdOnUsageRecords(List.of("u1"), "a1");
        verify(ratios).execute(ORG, "proj-1");
    }

    // ---- /{id}/accept -------------------------------------------------------------------------

    @Test
    void accept_sendsToApproval_withoutRatios() {
        AiUsageAllocation pending = allocation("a1", DAY, AiUsageAllocation.Status.PENDING_VALIDATION,
                AiUsageAllocation.AssignedBy.AGENT, "u1");
        when(repo.findById("a1", ORG)).thenReturn(Optional.of(pending));
        policyAnswers(ASKS);
        AcceptAllocationUseCase useCase = new AcceptAllocationUseCase(repo, items, ratios, approval);

        useCase.execute(ORG, USER, "a1");

        assertThat(pending.getStatus()).isEqualTo(AiUsageAllocation.Status.AWAITING_APPROVAL);
        assertThat(pending.getApproval().approverProjectIds()).containsExactly("p-1");
        assertThat(pending.getAssignedBy()).isEqualTo(AiUsageAllocation.AssignedBy.AGENT);
        verify(repo).updateAllocationIdOnUsageRecords(List.of("u1"), "a1");
        verify(ratios, never()).execute(any(), any());
    }

    @Test
    void accept_whenFree_validatesAndRecalculates() {
        AiUsageAllocation pending = allocation("a1", DAY, AiUsageAllocation.Status.PENDING_VALIDATION,
                AiUsageAllocation.AssignedBy.AUTO, "u1");
        when(repo.findById("a1", ORG)).thenReturn(Optional.of(pending));
        policyAnswers(Decision.free());

        new AcceptAllocationUseCase(repo, items, ratios, approval).execute(ORG, USER, "a1");

        assertThat(pending.getStatus()).isEqualTo(AiUsageAllocation.Status.VALIDATED);
        verify(ratios).execute(ORG, "proj-1");
    }

    // ---- /{id}/move ---------------------------------------------------------------------------

    @Test
    void move_toAnItemOutsideTheRepositoryProject_createsTheTargetAwaitingApproval() {
        AiUsageAllocation source = allocation("alloc-1", DAY, AiUsageAllocation.Status.VALIDATED,
                AiUsageAllocation.AssignedBy.AGENT, "u1", "u2");
        when(repo.findById("alloc-1", ORG)).thenReturn(Optional.of(source));
        ResolveAgentTaskUseCase resolve = mock(ResolveAgentTaskUseCase.class);
        when(resolve.resolveItem(ORG, USER, "wi-200")).thenReturn(Optional.of(
                new ResolvedTask("wi-200", "proj-2", "Task 200", "AB#200", AttributionSource.TASK_REF)));
        policyAnswers(ASKS);
        MoveAgentUsageUseCase move = new MoveAgentUsageUseCase(repo, resolve, agentQueries, ratios, items, approval);

        MoveAgentUsageUseCase.MoveResult result = move.execute(ORG, USER, "alloc-1", List.of("u1"), "wi-200");

        assertThat(result.target().status()).isEqualTo(AiUsageAllocation.Status.AWAITING_APPROVAL);
        assertThat(result.target().movedFromAgent()).isTrue();
        verify(repo).updateAllocationIdOnUsageRecords(List.of("u1"), "alloc-new");
        ArgumentCaptor<AiUsageAllocation> saved = ArgumentCaptor.forClass(AiUsageAllocation.class);
        verify(repo, org.mockito.Mockito.times(2)).save(saved.capture());
        assertThat(saved.getAllValues().get(0).getApproval().reasons()).containsExactly(REASON);
    }

    // ---- PUT /{day} (legacy) --------------------------------------------------------------------

    @Test
    void legacySave_sendsToApproval_andDoesNotCountTheReplacedAllocationsInTheBudget() {
        AiUsageAllocation old = allocation("a-old", DAY, AiUsageAllocation.Status.VALIDATED,
                AiUsageAllocation.AssignedBy.MANUAL, "u1");
        when(repo.findByOrgUserAndDay(ORG, USER, DAY)).thenReturn(List.of(old));
        AIUsage u = mock(AIUsage.class);
        when(u.getId()).thenReturn("u1");
        when(usages.findByOrganizationIdAndUserIdAndDayIn(ORG, USER, List.of(DAY))).thenReturn(List.of(u));
        policyAnswers(ASKS);
        SaveAllocationUseCase useCase = new SaveAllocationUseCase(repo, usages, items, eligibility, approval);

        useCase.execute(ORG, USER, DAY, List.of(new AllocationEntryInput("wi-1", "T", new BigDecimal("1.0000"))));

        ArgumentCaptor<AiUsageAllocation> saved = ArgumentCaptor.forClass(AiUsageAllocation.class);
        verify(repo).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(AiUsageAllocation.Status.AWAITING_APPROVAL);
        verify(approval).evaluate(eq(ORG), eq(USER), eq(DAY), eq(List.of("u1")), anyList(), eq(List.of("a-old")));
    }
}
