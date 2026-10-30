package andromeda.sei.ms.feature.allocation.application.usecase;

import andromeda.sei.ms.feature.allocation.application.usecase.ConfirmAllocationDaysUseCase.SkipReason;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation.ReviewCode;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation.ReviewReason;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocationRepository;
import andromeda.sei.ms.feature.allocation.domain.AllocationSplit;
import andromeda.sei.ms.feature.workitem.application.usecase.CalculateAiRatiosUseCase;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.domain.WorkItemRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** atribuicao-a-prova-de-esquecimento 1.3: confirming whole days. */
class ConfirmAllocationDaysUseCaseTest {

    static final String ORG = "org-1";
    static final String USER = "user-1";
    static final LocalDate D1 = LocalDate.of(2026, 10, 28);
    static final LocalDate D2 = LocalDate.of(2026, 10, 29);
    static final ReviewReason CLOSED = new ReviewReason(ReviewCode.ITEM_CLOSED, "wi-1", "AB#1", null);

    final AiUsageAllocationRepository repo = mock(AiUsageAllocationRepository.class);
    final WorkItemRepository items = mock(WorkItemRepository.class);
    final CalculateAiRatiosUseCase ratios = mock(CalculateAiRatiosUseCase.class);
    final ConfirmAllocationDaysUseCase useCase = new ConfirmAllocationDaysUseCase(repo, items, ratios);

    AiUsageAllocation pending(String id, LocalDate day, AiUsageAllocation.AssignedBy by, String wi, List<ReviewReason> reasons) {
        AiUsageAllocation a = new AiUsageAllocation(id, ORG, USER, day, List.of("u-" + id),
                List.of(new AllocationSplit(wi, "T", new BigDecimal("1.0000"))),
                AiUsageAllocation.Status.PENDING_VALIDATION, by, true, 0L, Instant.now(), Instant.now());
        a.setReviewReasons(reasons);
        return a;
    }

    void workItem(String id, String projectId) {
        WorkItem w = mock(WorkItem.class);
        when(w.getProjectId()).thenReturn(projectId);
        when(items.findByIdAndOrganizationIdAndActiveTrue(id, ORG)).thenReturn(Optional.of(w));
    }

    @Test
    void should_confirmAgentAndAutoPendingOfTheSameDay_keepingAssignedBy_andRecalculateRatiosOncePerProject() {
        AiUsageAllocation agent = pending("a1", D1, AiUsageAllocation.AssignedBy.AGENT, "wi-1", List.of());
        AiUsageAllocation auto = pending("a2", D1, AiUsageAllocation.AssignedBy.AUTO, "wi-2", List.of());
        when(repo.findByOrgUserAndDay(ORG, USER, D1)).thenReturn(List.of(agent, auto));
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        workItem("wi-1", "proj-1");
        workItem("wi-2", "proj-1");

        var result = useCase.execute(ORG, USER, List.of(D1), false);

        assertThat(result.confirmed()).containsExactly(D1);
        assertThat(result.skipped()).isEmpty();
        assertThat(agent.getStatus()).isEqualTo(AiUsageAllocation.Status.VALIDATED);
        assertThat(agent.getAssignedBy()).isEqualTo(AiUsageAllocation.AssignedBy.AGENT);
        assertThat(auto.getStatus()).isEqualTo(AiUsageAllocation.Status.VALIDATED);
        assertThat(auto.getAssignedBy()).isEqualTo(AiUsageAllocation.AssignedBy.AUTO);
        assertThat(agent.getReviewAcknowledgedAt()).isNull();
        verify(repo).updateAllocationIdOnUsageRecords(List.of("u-a1"), "a1");
        verify(ratios, times(1)).execute(ORG, "proj-1");
    }

    @Test
    void should_skipADayWithReviewReasons_unlessIncludeReview() {
        AiUsageAllocation flagged = pending("a1", D1, AiUsageAllocation.AssignedBy.AGENT, "wi-1", List.of(CLOSED));
        when(repo.findByOrgUserAndDay(ORG, USER, D1)).thenReturn(List.of(flagged));

        var skipped = useCase.execute(ORG, USER, List.of(D1), false);

        assertThat(skipped.confirmed()).isEmpty();
        assertThat(skipped.skipped()).containsExactly(new ConfirmAllocationDaysUseCase.Skipped(D1, SkipReason.NEEDS_REVIEW));
        assertThat(flagged.getStatus()).isEqualTo(AiUsageAllocation.Status.PENDING_VALIDATION);
        verify(repo, never()).save(any());

        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        workItem("wi-1", "proj-1");
        var confirmed = useCase.execute(ORG, USER, List.of(D1), true);

        assertThat(confirmed.confirmed()).containsExactly(D1);
        assertThat(flagged.getStatus()).isEqualTo(AiUsageAllocation.Status.VALIDATED);
        assertThat(flagged.getReviewAcknowledgedAt()).isNotNull();
    }

    @Test
    void should_reportNothingPending_forADayWithoutPendingOrWithOnlyConfirmedAndInactiveOnes() {
        AiUsageAllocation done = pending("a1", D1, AiUsageAllocation.AssignedBy.AGENT, "wi-1", List.of());
        done.setStatus(AiUsageAllocation.Status.VALIDATED);
        AiUsageAllocation inactive = pending("a2", D1, AiUsageAllocation.AssignedBy.AGENT, "wi-1", List.of());
        inactive.setActive(false);
        when(repo.findByOrgUserAndDay(ORG, USER, D1)).thenReturn(List.of(done, inactive));
        when(repo.findByOrgUserAndDay(ORG, USER, D2)).thenReturn(List.of());

        var result = useCase.execute(ORG, USER, List.of(D1, D2), true);

        assertThat(result.confirmed()).isEmpty();
        assertThat(result.skipped()).containsExactly(
                new ConfirmAllocationDaysUseCase.Skipped(D1, SkipReason.NOTHING_PENDING),
                new ConfirmAllocationDaysUseCase.Skipped(D2, SkipReason.NOTHING_PENDING));
        verify(repo, never()).save(any());
    }

    @Test
    void should_skipWithConflict_whenAnotherWriterChangedTheAllocation_andKeepConfirmingTheOtherDays() {
        AiUsageAllocation a1 = pending("a1", D1, AiUsageAllocation.AssignedBy.AGENT, "wi-1", List.of());
        AiUsageAllocation a2 = pending("a2", D2, AiUsageAllocation.AssignedBy.AGENT, "wi-1", List.of());
        when(repo.findByOrgUserAndDay(ORG, USER, D1)).thenReturn(List.of(a1));
        when(repo.findByOrgUserAndDay(ORG, USER, D2)).thenReturn(List.of(a2));
        when(repo.save(a1)).thenThrow(new OptimisticLockingFailureException("stale"));
        when(repo.save(a2)).thenReturn(a2);
        workItem("wi-1", "proj-1");

        var result = useCase.execute(ORG, USER, List.of(D1, D2), false);

        assertThat(result.confirmed()).containsExactly(D2);
        assertThat(result.skipped()).containsExactly(new ConfirmAllocationDaysUseCase.Skipped(D1, SkipReason.CONFLICT));
    }

    @Test
    void should_neverConfirmAnotherPersonsAllocation() {
        AiUsageAllocation foreign = pending("a1", D1, AiUsageAllocation.AssignedBy.AGENT, "wi-1", List.of());
        foreign.setUserId("someone-else");
        when(repo.findByOrgUserAndDay(ORG, USER, D1)).thenReturn(List.of(foreign));

        var result = useCase.execute(ORG, USER, List.of(D1), true);

        assertThat(result.skipped()).extracting(ConfirmAllocationDaysUseCase.Skipped::reason)
                .containsExactly(SkipReason.NOTHING_PENDING);
        assertThat(foreign.getStatus()).isEqualTo(AiUsageAllocation.Status.PENDING_VALIDATION);
        verify(repo, never()).save(any());
    }
}
