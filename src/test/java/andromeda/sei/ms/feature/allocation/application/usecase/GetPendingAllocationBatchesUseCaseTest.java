package andromeda.sei.ms.feature.allocation.application.usecase;

import andromeda.sei.ms.feature.aiusage.domain.AIUsage;
import andromeda.sei.ms.feature.aiusage.domain.AIUsageRepository;
import andromeda.sei.ms.feature.allocation.application.dto.PendingBatchOutput;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocationRepository;
import andromeda.sei.ms.feature.allocation.domain.AllocationSplit;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.domain.WorkItemRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetPendingAllocationBatchesUseCaseTest {

    @Mock
    private AIUsageRepository aiUsageRepository;
    @Mock
    private AiUsageAllocationRepository allocationRepository;
    @Mock
    private WorkItemRepository workItemRepository;

    private GetPendingAllocationBatchesUseCase useCase;

    private static final String ORG = "org-1";
    private static final String USER = "user-1";
    private static final LocalDate DAY = LocalDate.of(2026, 6, 3);

    @BeforeEach
    void setUp() {
        useCase = new GetPendingAllocationBatchesUseCase(aiUsageRepository, allocationRepository, workItemRepository);
    }

    /**
     * Key scenario from task 1.8:
     * All usage records for a day have allocationId set to a PENDING_VALIDATION allocation.
     * After the fix, findUnallocatedByOrganizationIdAndUserId returns empty (records have allocationId set),
     * but the PENDING_VALIDATION allocation exists — so the day must still surface in results.
     */
    @Test
    void should_surfaceDay_when_allUsageHasAllocationIdPointingToPendingValidationAllocation() {
        // Usage records already have allocationId set (pre-fix state), so they won't appear in unallocated query
        when(aiUsageRepository.findUnallocatedByOrganizationIdAndUserId(ORG, USER))
                .thenReturn(List.of());

        // But a PENDING_VALIDATION allocation exists for that day
        AllocationSplit split = new AllocationSplit("wi-1", "Fix login", new BigDecimal("1.0000"));
        AiUsageAllocation pendingAllocation = new AiUsageAllocation(
                "alloc-1", ORG, USER, DAY,
                List.of("usage-1", "usage-2"), List.of(split),
                AiUsageAllocation.Status.PENDING_VALIDATION, AiUsageAllocation.AssignedBy.AUTO,
                true, 0L, Instant.now(), Instant.now());

        when(allocationRepository.findByOrgUserAndStatus(ORG, USER, AiUsageAllocation.Status.PENDING_VALIDATION))
                .thenReturn(List.of(pendingAllocation));

        // The covered usage records for that day
        AIUsage usage1 = aiUsage("usage-1", DAY, "alloc-1");
        AIUsage usage2 = aiUsage("usage-2", DAY, "alloc-1");
        when(aiUsageRepository.findByOrganizationIdAndUserIdAndDayIn(ORG, USER, List.of(DAY)))
                .thenReturn(List.of(usage1, usage2));

        List<PendingBatchOutput> results = useCase.execute(ORG, USER);

        assertThat(results).hasSize(1);
        PendingBatchOutput batch = results.get(0);
        assertThat(batch.day()).isEqualTo(DAY);
        assertThat(batch.suggestionAllocationId()).isEqualTo("alloc-1");
        assertThat(batch.suggestion()).hasSize(1);
        assertThat(batch.suggestion().get(0).workItemId()).isEqualTo("wi-1");
    }

    @Test
    void should_surfaceDay_when_usageHasNullAllocationId_andNoPendingAllocationExists() {
        // Day with unallocated usage (no auto-suggestion was possible)
        AIUsage unallocated = aiUsage("usage-3", DAY, null);
        when(aiUsageRepository.findUnallocatedByOrganizationIdAndUserId(ORG, USER))
                .thenReturn(List.of(unallocated));

        when(allocationRepository.findByOrgUserAndStatus(ORG, USER, AiUsageAllocation.Status.PENDING_VALIDATION))
                .thenReturn(List.of());

        List<PendingBatchOutput> results = useCase.execute(ORG, USER);

        assertThat(results).hasSize(1);
        PendingBatchOutput batch = results.get(0);
        assertThat(batch.day()).isEqualTo(DAY);
        assertThat(batch.suggestionAllocationId()).isNull();
        assertThat(batch.suggestion()).isEmpty();
    }

    @Test
    void should_returnEmpty_when_noUnallocatedUsageAndNoPendingAllocations() {
        when(aiUsageRepository.findUnallocatedByOrganizationIdAndUserId(ORG, USER))
                .thenReturn(List.of());
        when(allocationRepository.findByOrgUserAndStatus(ORG, USER, AiUsageAllocation.Status.PENDING_VALIDATION))
                .thenReturn(List.of());

        List<PendingBatchOutput> results = useCase.execute(ORG, USER);

        assertThat(results).isEmpty();
    }

    @Test
    void should_notDuplicateDay_when_bothPendingAllocationAndNullUsageExistForSameDay() {
        // PENDING_VALIDATION allocation exists
        AiUsageAllocation pendingAllocation = new AiUsageAllocation(
                "alloc-1", ORG, USER, DAY,
                List.of("usage-1"), List.of(),
                AiUsageAllocation.Status.PENDING_VALIDATION, AiUsageAllocation.AssignedBy.AUTO,
                true, 0L, Instant.now(), Instant.now());
        when(allocationRepository.findByOrgUserAndStatus(ORG, USER, AiUsageAllocation.Status.PENDING_VALIDATION))
                .thenReturn(List.of(pendingAllocation));

        // There is also an unallocated record for the same day (edge case)
        AIUsage unallocated = aiUsage("usage-2", DAY, null);
        when(aiUsageRepository.findUnallocatedByOrganizationIdAndUserId(ORG, USER))
                .thenReturn(List.of(unallocated));

        AIUsage usage1 = aiUsage("usage-1", DAY, "alloc-1");
        when(aiUsageRepository.findByOrganizationIdAndUserIdAndDayIn(ORG, USER, List.of(DAY)))
                .thenReturn(List.of(usage1));

        List<PendingBatchOutput> results = useCase.execute(ORG, USER);

        // Day should appear only once (PENDING allocation takes priority for that day)
        assertThat(results).hasSize(1);
        assertThat(results.get(0).day()).isEqualTo(DAY);
        assertThat(results.get(0).suggestionAllocationId()).isEqualTo("alloc-1");
    }

    @Test
    void should_sumCacheTokensPerBatch_andKeepInputAndOutputUntouched() {
        AIUsage agent = aiUsage("u1", DAY, null);
        agent.setCacheReadTokens(4000L);
        agent.setCacheWriteTokens(300L);
        AIUsage aief = aiUsage("u2", DAY, null); // AIEF: cache stays 0 (no fields)
        when(aiUsageRepository.findUnallocatedByOrganizationIdAndUserId(ORG, USER)).thenReturn(List.of(agent, aief));
        when(allocationRepository.findByOrgUserAndStatus(ORG, USER, AiUsageAllocation.Status.PENDING_VALIDATION))
                .thenReturn(List.of());

        PendingBatchOutput batch = useCase.execute(ORG, USER).get(0);

        assertThat(batch.totalCacheReadTokens()).isEqualTo(4000L);
        assertThat(batch.totalCacheWriteTokens()).isEqualTo(300L);
        assertThat(batch.totalInputTokens()).isEqualTo(2000L);
        assertThat(batch.totalOutputTokens()).isEqualTo(1000L);
    }

    private AiUsageAllocation agentPending(String id, String wi, List<String> usageIds, AiUsageAllocation.ResolvedBy by,
                                           List<AiUsageAllocation.ReviewReason> reasons) {
        AiUsageAllocation a = new AiUsageAllocation(id, ORG, USER, DAY, usageIds,
                List.of(new AllocationSplit(wi, "T-" + wi, new BigDecimal("1.0000"))),
                AiUsageAllocation.Status.PENDING_VALIDATION, AiUsageAllocation.AssignedBy.AGENT,
                true, 0L, Instant.now(), Instant.now());
        a.setAgent(new AiUsageAllocation.AgentInfo(by, List.of(), List.of(), "AB#9"));
        a.setReviewReasons(reasons);
        return a;
    }

    @Test
    void should_joinTheAgentAndTheAutoPendingOfTheDay_weightingBySharesByTokens() {
        // agent: u1 (1500 tokens) on wi-1; agent: u2+u3 (3000) on wi-2; auto: u4 (1500) with 50/50 on wi-2 and wi-3
        AiUsageAllocation a1 = agentPending("a1", "wi-1", List.of("u1"), AiUsageAllocation.ResolvedBy.BRANCH, List.of());
        AiUsageAllocation a2 = agentPending("a2", "wi-2", List.of("u2", "u3"), AiUsageAllocation.ResolvedBy.MARK, List.of());
        AiUsageAllocation auto = new AiUsageAllocation("a3", ORG, USER, DAY, List.of("u4"),
                List.of(new AllocationSplit("wi-2", "T-wi-2", new BigDecimal("0.5000")),
                        new AllocationSplit("wi-3", "T-wi-3", new BigDecimal("0.5000"))),
                AiUsageAllocation.Status.PENDING_VALIDATION, AiUsageAllocation.AssignedBy.AUTO,
                true, 0L, Instant.now(), Instant.now(), AiUsageAllocation.SuggestionSource.AZURE_ACTIVITY);
        when(aiUsageRepository.findUnallocatedByOrganizationIdAndUserId(ORG, USER)).thenReturn(List.of());
        when(allocationRepository.findByOrgUserAndStatus(ORG, USER, AiUsageAllocation.Status.PENDING_VALIDATION))
                .thenReturn(List.of(a1, a2, auto));
        when(aiUsageRepository.findByOrganizationIdAndUserIdAndDayIn(ORG, USER, List.of(DAY)))
                .thenReturn(List.of(aiUsage("u1", DAY, "a1"), aiUsage("u2", DAY, "a2"), aiUsage("u3", DAY, "a2"),
                        aiUsage("u4", DAY, null)));
        WorkItem item = new WorkItem("wi-1", ORG, "proj-1", WorkItem.Source.AZURE_DEVOPS, "123", Instant.now(), "T",
                "Task", WorkItem.ResolvedType.TASK, "Active", WorkItem.ResolvedStatus.IN_PROGRESS, "Medium", List.of(),
                "sprint-1", null, null, null, null, Instant.now(), Instant.now(), null, null, null, true, 1L,
                Instant.now(), null, null, null, null, null, null, null, null);
        when(workItemRepository.findByIdAndOrganizationIdAndActiveTrue("wi-1", ORG)).thenReturn(Optional.of(item));

        List<PendingBatchOutput> results = useCase.execute(ORG, USER);

        assertThat(results).hasSize(1);
        PendingBatchOutput b = results.get(0);
        assertThat(b.allocationIds()).containsExactly("a1", "a2", "a3");
        assertThat(b.suggestionAllocationId()).isEqualTo("a1");
        // weights: wi-1 = 1500; wi-2 = 3000 + 750; wi-3 = 750; sum 6000
        assertThat(b.suggestion()).extracting("workItemId").containsExactly("wi-1", "wi-2", "wi-3");
        assertThat(b.suggestion().get(0).percentage()).isEqualByComparingTo("0.2500");
        assertThat(b.suggestion().get(1).percentage()).isEqualByComparingTo("0.6250");
        assertThat(b.suggestion().get(2).percentage()).isEqualByComparingTo("0.1250");
        assertThat(b.suggestion().stream().map(s -> s.percentage()).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("1.0000");
        assertThat(b.suggestion().get(0).displayId()).isEqualTo("AB#123");
        assertThat(b.suggestion().get(0).source()).isEqualTo("BRANCH");
        assertThat(b.suggestion().get(1).source()).isEqualTo("MARK");
        assertThat(b.suggestion().get(2).source()).isEqualTo("AZURE_ACTIVITY");
        assertThat(b.suggestion().get(0).overriddenMark().displayId()).isEqualTo("AB#9");
        assertThat(b.suggestion().get(2).overriddenMark()).isNull();
        assertThat(b.review().state()).isEqualTo("SUGGESTED");
    }

    @Test
    void should_lastEntryAbsorbTheRounding_whenThreeEqualShares() {
        AiUsageAllocation a1 = agentPending("a1", "wi-1", List.of("u1"), AiUsageAllocation.ResolvedBy.BRANCH, List.of());
        AiUsageAllocation a2 = agentPending("a2", "wi-2", List.of("u2"), AiUsageAllocation.ResolvedBy.BRANCH, List.of());
        AiUsageAllocation a3 = agentPending("a3", "wi-3", List.of("u3"), AiUsageAllocation.ResolvedBy.BRANCH, List.of());
        when(aiUsageRepository.findUnallocatedByOrganizationIdAndUserId(ORG, USER)).thenReturn(List.of());
        when(allocationRepository.findByOrgUserAndStatus(ORG, USER, AiUsageAllocation.Status.PENDING_VALIDATION))
                .thenReturn(List.of(a1, a2, a3));
        when(aiUsageRepository.findByOrganizationIdAndUserIdAndDayIn(ORG, USER, List.of(DAY)))
                .thenReturn(List.of(aiUsage("u1", DAY, "a1"), aiUsage("u2", DAY, "a2"), aiUsage("u3", DAY, "a3")));

        PendingBatchOutput b = useCase.execute(ORG, USER).get(0);

        assertThat(b.suggestion().get(0).percentage()).isEqualByComparingTo("0.3333");
        assertThat(b.suggestion().get(2).percentage()).isEqualByComparingTo("0.3334");
    }

    @Test
    void should_markTheDayNeedsReview_withTheUnionOfTheReasonsWithoutRepeating() {
        AiUsageAllocation a1 = agentPending("a1", "wi-1", List.of("u1"), AiUsageAllocation.ResolvedBy.BRANCH,
                List.of(new AiUsageAllocation.ReviewReason(AiUsageAllocation.ReviewCode.ITEM_CLOSED, "wi-1", "AB#1", null),
                        new AiUsageAllocation.ReviewReason(AiUsageAllocation.ReviewCode.USAGE_SPIKE, null, null, 6L)));
        AiUsageAllocation a2 = agentPending("a2", "wi-2", List.of("u2"), AiUsageAllocation.ResolvedBy.BRANCH,
                List.of(new AiUsageAllocation.ReviewReason(AiUsageAllocation.ReviewCode.USAGE_SPIKE, null, null, 6L),
                        new AiUsageAllocation.ReviewReason(AiUsageAllocation.ReviewCode.ITEM_CLOSED, "wi-2", "AB#2", null)));
        when(aiUsageRepository.findUnallocatedByOrganizationIdAndUserId(ORG, USER)).thenReturn(List.of());
        when(allocationRepository.findByOrgUserAndStatus(ORG, USER, AiUsageAllocation.Status.PENDING_VALIDATION))
                .thenReturn(List.of(a1, a2));
        when(aiUsageRepository.findByOrganizationIdAndUserIdAndDayIn(ORG, USER, List.of(DAY)))
                .thenReturn(List.of(aiUsage("u1", DAY, "a1"), aiUsage("u2", DAY, "a2")));

        PendingBatchOutput b = useCase.execute(ORG, USER).get(0);

        assertThat(b.review().state()).isEqualTo("NEEDS_REVIEW");
        assertThat(b.review().reasons()).containsExactly("ITEM_CLOSED", "USAGE_SPIKE");
        assertThat(b.review().details()).hasSize(3);
    }

    @Test
    void should_ignoreInactivePendingAllocations_absorbedByAnAdjustment() {
        AiUsageAllocation a1 = agentPending("a1", "wi-1", List.of("u1"), AiUsageAllocation.ResolvedBy.BRANCH, List.of());
        a1.setActive(false);
        when(aiUsageRepository.findUnallocatedByOrganizationIdAndUserId(ORG, USER)).thenReturn(List.of());
        when(allocationRepository.findByOrgUserAndStatus(ORG, USER, AiUsageAllocation.Status.PENDING_VALIDATION))
                .thenReturn(List.of(a1));

        assertThat(useCase.execute(ORG, USER)).isEmpty();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private AIUsage aiUsage(String id, LocalDate day, String allocationId) {
        return new AIUsage(
                id, ORG, USER, "gh-login", "dataset-1", day, "gpt-4o",
                "ws-1", "Workspace 1", "mc-1", "Machine 1",
                1000L, 500L, 10, "vscode", allocationId,
                new BigDecimal("0.05"), Instant.now(), true, null, Instant.now(), Instant.now()
        );
    }
}
