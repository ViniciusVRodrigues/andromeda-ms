package andromeda.sei.ms.feature.allocation.application.usecase;

import andromeda.sei.ms.feature.allocation.application.AllocationApproverAccess;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocationRepository;
import andromeda.sei.ms.feature.allocation.domain.AllocationSplit;
import andromeda.sei.ms.feature.allocation.domain.exception.AllocationApprovalForbiddenException;
import andromeda.sei.ms.feature.allocation.domain.exception.AllocationApprovalSelfException;
import andromeda.sei.ms.feature.allocation.domain.exception.AllocationNotAwaitingApprovalException;
import andromeda.sei.ms.feature.allocation.domain.exception.AllocationNotFoundException;
import andromeda.sei.ms.feature.allocation.domain.exception.AllocationReturnNoteInvalidException;
import andromeda.sei.ms.feature.allocation.domain.exception.AllocationVersionConflictException;
import andromeda.sei.ms.feature.user.domain.User;
import andromeda.sei.ms.feature.user.domain.UserRepository;
import andromeda.sei.ms.feature.workitem.application.usecase.CalculateAiRatiosUseCase;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.domain.WorkItemRepository;
import andromeda.sei.ms.shared.application.security.EffectivePermissionService;
import andromeda.sei.ms.shared.domain.security.UserPermissionSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static andromeda.sei.ms.feature.allocation.application.usecase.ApprovalQueueTestData.CODE;
import static andromeda.sei.ms.feature.allocation.application.usecase.ApprovalQueueTestData.ORG;
import static andromeda.sei.ms.feature.allocation.application.usecase.ApprovalQueueTestData.awaiting;
import static andromeda.sei.ms.feature.allocation.application.usecase.ApprovalQueueTestData.caller;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DecideAllocationApprovalUseCaseTest {

    private static final Instant T0 = Instant.parse("2026-10-29T10:00:00Z");

    @Mock AiUsageAllocationRepository allocationRepository;
    @Mock EffectivePermissionService effectivePermissionService;
    @Mock UserRepository userRepository;
    @Mock WorkItemRepository workItemRepository;
    @Mock CalculateAiRatiosUseCase calculateAiRatiosUseCase;

    UserPermissionSnapshot lead;

    private DecideAllocationApprovalUseCase useCase() {
        return new DecideAllocationApprovalUseCase(allocationRepository,
                new AllocationApproverAccess(effectivePermissionService), userRepository, workItemRepository,
                calculateAiRatiosUseCase);
    }

    @BeforeEach
    void setUp() {
        lead = caller("lead", false);
        org.mockito.Mockito.lenient().when(effectivePermissionService.projectIdsWith(lead, CODE)).thenReturn(Set.of("p-1"));
    }

    private AiUsageAllocation stored(AiUsageAllocation a) {
        when(allocationRepository.findById(a.getId(), ORG)).thenReturn(Optional.of(a));
        return a;
    }

    @Test
    void approve_validatesTheDay_recordsTheDecision_stampsTheRowsAndRunsTheRatios() {
        AiUsageAllocation a = awaiting("a-1", "ana", List.of("p-1"), T0);
        a.setSplits(List.of(new AllocationSplit("wi-1", "Fix", BigDecimal.ONE)));
        stored(a);
        WorkItem wi = org.mockito.Mockito.mock(WorkItem.class);
        when(wi.getProjectId()).thenReturn("p-7");
        when(workItemRepository.findByIdAndOrganizationIdAndActiveTrue("wi-1", ORG)).thenReturn(Optional.of(wi));

        useCase().approve(lead, "a-1");

        ArgumentCaptor<AiUsageAllocation> saved = ArgumentCaptor.forClass(AiUsageAllocation.class);
        verify(allocationRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(AiUsageAllocation.Status.VALIDATED);
        AiUsageAllocation.Approval ap = saved.getValue().getApproval();
        assertThat(ap.decision()).isEqualTo(AiUsageAllocation.ApprovalDecision.APPROVED);
        assertThat(ap.decidedBy()).isEqualTo("lead");
        assertThat(ap.decidedAt()).isNotNull();
        assertThat(ap.note()).isNull();
        assertThat(ap.reasons()).hasSize(1);
        verify(allocationRepository).updateAllocationIdOnUsageRecords(List.of("u-a-1"), "a-1");
        verify(calculateAiRatiosUseCase).execute(ORG, "p-7");
    }

    @Test
    void approve_rejectsTheOwnDay_beforeAnythingElse() {
        stored(awaiting("a-1", "lead", List.of("p-1"), T0));

        assertThatThrownBy(() -> useCase().approve(lead, "a-1")).isInstanceOf(AllocationApprovalSelfException.class)
                .extracting("code").isEqualTo("ALLOCATION_APPROVAL_SELF");
        verify(allocationRepository, never()).save(any());
    }

    @Test
    void approve_rejectsACallerWithoutThePermissionOnTheApproverProjects() {
        stored(awaiting("a-1", "ana", List.of("p-2"), T0));

        assertThatThrownBy(() -> useCase().approve(lead, "a-1")).isInstanceOf(AllocationApprovalForbiddenException.class)
                .extracting("code").isEqualTo("ALLOCATION_APPROVAL_FORBIDDEN");
        verify(allocationRepository, never()).save(any());
    }

    @Test
    void approve_answersNotFound_forAnotherOrganizationOrAMissingId() {
        when(allocationRepository.findById("x", ORG)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase().approve(lead, "x")).isInstanceOf(AllocationNotFoundException.class)
                .extracting("code").isEqualTo("ALLOCATION_NOT_FOUND");
    }

    @Test
    void approve_answersConflictWithWhoDecided_whenTheDayIsNotAwaitingAnymore() {
        AiUsageAllocation a = awaiting("a-1", "ana", List.of("p-1"), T0);
        a.setStatus(AiUsageAllocation.Status.VALIDATED);
        a.setApproval(new AiUsageAllocation.Approval(a.getApproval().reasons(), List.of("p-1"), T0,
                AiUsageAllocation.ApprovalDecision.APPROVED, "other-lead", T0, null));
        stored(a);
        User other = org.mockito.Mockito.mock(User.class);
        when(other.getName()).thenReturn("Outro Lead");
        when(userRepository.findByIdAndOrganizationId("other-lead", ORG)).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> useCase().approve(lead, "a-1"))
                .isInstanceOfSatisfying(AllocationNotAwaitingApprovalException.class, e -> {
                    assertThat(e.getCode()).isEqualTo("ALLOCATION_NOT_AWAITING_APPROVAL");
                    assertThat(e.getDecidedByName()).isEqualTo("Outro Lead");
                });
    }

    @Test
    void approve_answersConflict_whenSomeoneChangedTheDayMeanwhile() {
        stored(awaiting("a-1", "ana", List.of("p-1"), T0));
        when(allocationRepository.save(any())).thenThrow(new OptimisticLockingFailureException("race"));

        assertThatThrownBy(() -> useCase().approve(lead, "a-1")).isInstanceOf(AllocationVersionConflictException.class);
        verify(calculateAiRatiosUseCase, never()).execute(anyString(), anyString());
    }

    @Test
    void approveBatch_approvesWhatItCanAndSkipsTheRestByReason() {
        stored(awaiting("ok", "ana", List.of("p-1"), T0));
        stored(awaiting("self", "lead", List.of("p-1"), T0));
        stored(awaiting("forbidden", "ana", List.of("p-2"), T0));
        AiUsageAllocation done = awaiting("done", "ana", List.of("p-1"), T0);
        done.setStatus(AiUsageAllocation.Status.VALIDATED);
        done.setApproval(new AiUsageAllocation.Approval(List.of(), List.of("p-1"), T0,
                AiUsageAllocation.ApprovalDecision.APPROVED, "other-lead", T0, null));
        stored(done);
        User other = org.mockito.Mockito.mock(User.class);
        when(other.getName()).thenReturn("Outro Lead");
        when(userRepository.findByIdAndOrganizationId("other-lead", ORG)).thenReturn(Optional.of(other));
        stored(awaiting("race", "ana", List.of("p-1"), T0));
        when(allocationRepository.save(any())).thenAnswer(inv -> {
            AiUsageAllocation a = inv.getArgument(0);
            if ("race".equals(a.getId())) throw new OptimisticLockingFailureException("race");
            return a;
        });
        when(allocationRepository.findById("missing", ORG)).thenReturn(Optional.empty());

        DecideAllocationApprovalUseCase.BatchResult result = useCase().approveBatch(lead,
                List.of("ok", "self", "forbidden", "done", "race", "missing", "ok"));

        assertThat(result.approved()).containsExactly("ok");
        assertThat(result.skipped()).extracting(DecideAllocationApprovalUseCase.Skipped::id,
                        DecideAllocationApprovalUseCase.Skipped::reason, DecideAllocationApprovalUseCase.Skipped::decidedByName)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("self", DecideAllocationApprovalUseCase.SkipReason.SELF, null),
                        org.assertj.core.groups.Tuple.tuple("forbidden", DecideAllocationApprovalUseCase.SkipReason.FORBIDDEN, null),
                        org.assertj.core.groups.Tuple.tuple("done", DecideAllocationApprovalUseCase.SkipReason.NOT_AWAITING, "Outro Lead"),
                        org.assertj.core.groups.Tuple.tuple("race", DecideAllocationApprovalUseCase.SkipReason.CONFLICT, null),
                        org.assertj.core.groups.Tuple.tuple("missing", DecideAllocationApprovalUseCase.SkipReason.NOT_FOUND, null));
    }

    @Test
    void returnToOwner_goesBackToPending_withTheSentenceAsReviewReasonAndTheDecisionRecorded() {
        AiUsageAllocation a = awaiting("a-1", "ana", List.of("p-1"), T0);
        a.setReviewReasons(List.of(new AiUsageAllocation.ReviewReason(AiUsageAllocation.ReviewCode.USAGE_SPIKE, null, null, 3L)));
        stored(a);

        useCase().returnToOwner(lead, "a-1", "  Separe o estudo do trabalho do projeto  ");

        ArgumentCaptor<AiUsageAllocation> saved = ArgumentCaptor.forClass(AiUsageAllocation.class);
        verify(allocationRepository).save(saved.capture());
        AiUsageAllocation s = saved.getValue();
        assertThat(s.getStatus()).isEqualTo(AiUsageAllocation.Status.PENDING_VALIDATION);
        assertThat(s.getSplits()).hasSize(1);
        assertThat(s.getApproval().decision()).isEqualTo(AiUsageAllocation.ApprovalDecision.RETURNED);
        assertThat(s.getApproval().decidedBy()).isEqualTo("lead");
        assertThat(s.getApproval().decidedAt()).isNotNull();
        assertThat(s.getApproval().note()).isEqualTo("Separe o estudo do trabalho do projeto");
        assertThat(s.getReviewReasons()).extracting(AiUsageAllocation.ReviewReason::code)
                .containsExactly(AiUsageAllocation.ReviewCode.USAGE_SPIKE, AiUsageAllocation.ReviewCode.RETURNED);
        assertThat(s.getReviewReasons().get(1).note()).isEqualTo("Separe o estudo do trabalho do projeto");
        verify(calculateAiRatiosUseCase, never()).execute(anyString(), anyString());
    }

    @Test
    void returnToOwner_replacesAnEarlierReturnedReason() {
        AiUsageAllocation a = awaiting("a-1", "ana", List.of("p-1"), T0);
        a.setReviewReasons(List.of(new AiUsageAllocation.ReviewReason(AiUsageAllocation.ReviewCode.RETURNED,
                null, null, null, "frase antiga")));
        stored(a);

        useCase().returnToOwner(lead, "a-1", "nova frase");

        assertThat(a.getReviewReasons()).hasSize(1);
        assertThat(a.getReviewReasons().get(0).note()).isEqualTo("nova frase");
    }

    @Test
    void returnToOwner_rejectsABlankOrTooLongNote_withoutReadingAnything() {
        assertThatThrownBy(() -> useCase().returnToOwner(lead, "a-1", "   "))
                .isInstanceOf(AllocationReturnNoteInvalidException.class);
        assertThatThrownBy(() -> useCase().returnToOwner(lead, "a-1", "x".repeat(281)))
                .isInstanceOf(AllocationReturnNoteInvalidException.class);
        verify(allocationRepository, never()).findById(anyString(), eq(ORG));
    }

    @Test
    void returnToOwner_rejectsTheOwnDayAndOtherApprovers() {
        stored(awaiting("own", "lead", List.of("p-1"), T0));
        stored(awaiting("foreign", "ana", List.of("p-2"), T0));

        assertThatThrownBy(() -> useCase().returnToOwner(lead, "own", "nota"))
                .isInstanceOf(AllocationApprovalSelfException.class);
        assertThatThrownBy(() -> useCase().returnToOwner(lead, "foreign", "nota"))
                .isInstanceOf(AllocationApprovalForbiddenException.class);
        verify(allocationRepository, never()).save(any());
    }
}
