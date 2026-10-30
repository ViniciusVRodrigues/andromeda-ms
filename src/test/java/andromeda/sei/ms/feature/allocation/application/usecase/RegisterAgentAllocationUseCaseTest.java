package andromeda.sei.ms.feature.allocation.application.usecase;

import andromeda.sei.ms.feature.allocation.application.AgentReviewReasonCalculator;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation.ReviewCode;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation.ReviewReason;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation.ResolvedBy;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocationRepository;
import andromeda.sei.ms.feature.allocation.domain.AllocationSplit;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RegisterAgentAllocationUseCaseTest {

    static final LocalDate DAY = LocalDate.of(2026, 10, 29);
    final AiUsageAllocationRepository repo = mock(AiUsageAllocationRepository.class);
    final AgentReviewReasonCalculator reasons = mock(AgentReviewReasonCalculator.class);
    final RegisterAgentAllocationUseCase useCase = new RegisterAgentAllocationUseCase(repo, reasons);

    RegisterAgentAllocationUseCase.Registration reg(String wi, List<String> ids, ResolvedBy by, String branch) {
        return new RegisterAgentAllocationUseCase.Registration("org-1", "user-1", DAY, wi, "T", "proj-1", ids, by,
                List.of("github.com/a/b"), List.of(branch));
    }

    @Test
    void should_createAPendingAgentAllocationWithAWholeSplitAndTheReviewReasons() {
        when(repo.findByOrgUserAndDay("org-1", "user-1", DAY)).thenReturn(List.of());
        when(reasons.reasonsFor("org-1", "user-1", DAY, "wi-1"))
                .thenReturn(List.of(new ReviewReason(ReviewCode.ITEM_CLOSED, "wi-1", "AB#1", null)));
        when(repo.save(any())).thenAnswer(i -> {
            AiUsageAllocation a = i.getArgument(0);
            a.setId("alloc-1");
            return a;
        });

        String id = useCase.execute(reg("wi-1", List.of("u1", "u2"), ResolvedBy.BRANCH, "feature/x"));

        assertThat(id).isEqualTo("alloc-1");
        ArgumentCaptor<AiUsageAllocation> c = ArgumentCaptor.forClass(AiUsageAllocation.class);
        verify(repo).save(c.capture());
        AiUsageAllocation a = c.getValue();
        assertThat(a.getAssignedBy()).isEqualTo(AiUsageAllocation.AssignedBy.AGENT);
        assertThat(a.getStatus()).isEqualTo(AiUsageAllocation.Status.PENDING_VALIDATION);
        assertThat(a.getReviewReasons()).extracting(ReviewReason::code).containsExactly(ReviewCode.ITEM_CLOSED);
        assertThat(a.getSplits().get(0).getPercentage()).isEqualByComparingTo("1.0000");
        assertThat(a.getAgent().resolvedBy()).isEqualTo(ResolvedBy.BRANCH);
        verify(repo).updateAllocationIdOnUsageRecords(List.of("u1", "u2"), "alloc-1");
    }

    @Test
    void should_mergeIntoTheExistingAgentAllocationOfTheSameWorkItem_keepingTheStrongerResolution() {
        AiUsageAllocation existing = new AiUsageAllocation("alloc-1", "org-1", "user-1", DAY, List.of("u1"),
                List.of(new AllocationSplit("wi-1", "T", new BigDecimal("1.0000"))),
                AiUsageAllocation.Status.PENDING_VALIDATION, AiUsageAllocation.AssignedBy.AGENT, true, 0L, Instant.now(), Instant.now());
        existing.setAgent(new AiUsageAllocation.AgentInfo(ResolvedBy.SINGLE_ACTIVE, List.of(), List.of("b1")));
        when(repo.findByOrgUserAndDay("org-1", "user-1", DAY)).thenReturn(List.of(existing));
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));

        String id = useCase.execute(reg("wi-1", List.of("u2"), ResolvedBy.MARK, "b2"));

        assertThat(id).isEqualTo("alloc-1");
        assertThat(existing.getAiUsageIds()).containsExactly("u1", "u2");
        assertThat(existing.getAgent().resolvedBy()).isEqualTo(ResolvedBy.MARK);
        assertThat(existing.getAgent().branches()).containsExactly("b1", "b2");
    }

    @Test
    void should_createASeparateAllocation_whenTheDayOnlyHasAManualOneOrAnotherWorkItem() {
        AiUsageAllocation manual = new AiUsageAllocation("alloc-m", "org-1", "user-1", DAY, List.of("u9"),
                List.of(new AllocationSplit("wi-1", "T", new BigDecimal("1.0000"))),
                AiUsageAllocation.Status.VALIDATED, AiUsageAllocation.AssignedBy.MANUAL, true, 0L, Instant.now(), Instant.now());
        when(repo.findByOrgUserAndDay("org-1", "user-1", DAY)).thenReturn(List.of(manual));
        when(repo.save(any())).thenAnswer(i -> {
            AiUsageAllocation a = i.getArgument(0);
            a.setId("alloc-new");
            return a;
        });

        String id = useCase.execute(reg("wi-1", List.of("u1"), ResolvedBy.BRANCH, "b"));

        assertThat(id).isEqualTo("alloc-new");
        assertThat(manual.getAiUsageIds()).containsExactly("u9");
    }

    @Test
    void should_startANewPendingAllocation_whenUsageArrivesAfterThePersonConfirmed() {
        AiUsageAllocation confirmed = new AiUsageAllocation("alloc-c", "org-1", "user-1", DAY, List.of("u1"),
                List.of(new AllocationSplit("wi-1", "T", new BigDecimal("1.0000"))),
                AiUsageAllocation.Status.VALIDATED, AiUsageAllocation.AssignedBy.AGENT, true, 0L, Instant.now(), Instant.now());
        when(repo.findByOrgUserAndDay("org-1", "user-1", DAY)).thenReturn(List.of(confirmed));
        when(repo.save(any())).thenAnswer(i -> {
            AiUsageAllocation a = i.getArgument(0);
            a.setId("alloc-new");
            return a;
        });

        String id = useCase.execute(reg("wi-1", List.of("u2"), ResolvedBy.BRANCH, "b"));

        assertThat(id).isEqualTo("alloc-new");
        assertThat(confirmed.getAiUsageIds()).containsExactly("u1");
        verify(repo).updateAllocationIdOnUsageRecords(List.of("u2"), "alloc-new");
    }

    @Test
    void should_ignoreAnInactivePendingAgentAllocation() {
        AiUsageAllocation inactive = new AiUsageAllocation("alloc-i", "org-1", "user-1", DAY, List.of("u1"),
                List.of(new AllocationSplit("wi-1", "T", new BigDecimal("1.0000"))),
                AiUsageAllocation.Status.PENDING_VALIDATION, AiUsageAllocation.AssignedBy.AGENT, false, 0L, Instant.now(), Instant.now());
        when(repo.findByOrgUserAndDay("org-1", "user-1", DAY)).thenReturn(List.of(inactive));
        when(repo.save(any())).thenAnswer(i -> {
            AiUsageAllocation a = i.getArgument(0);
            a.setId("alloc-new");
            return a;
        });

        assertThat(useCase.execute(reg("wi-1", List.of("u2"), ResolvedBy.BRANCH, "b"))).isEqualTo("alloc-new");
    }
}
