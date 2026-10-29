package andromeda.sei.ms.feature.allocation.application.usecase;

import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocationRepository;
import andromeda.sei.ms.feature.allocation.domain.exception.AllocationAlreadyValidatedException;
import andromeda.sei.ms.feature.allocation.domain.exception.AllocationNotFoundException;
import andromeda.sei.ms.feature.workitem.application.usecase.CalculateAiRatiosUseCase;
import andromeda.sei.ms.feature.workitem.domain.WorkItemRepository;
import andromeda.sei.ms.shared.domain.exception.ForbiddenOperationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AcceptAllocationUseCaseTest {

    @Mock
    private AiUsageAllocationRepository allocationRepository;

    @Mock
    private WorkItemRepository workItemRepository;

    @Mock
    private CalculateAiRatiosUseCase calculateAiRatiosUseCase;

    @InjectMocks
    private AcceptAllocationUseCase useCase;

    private static final String ORG_ID = "org-1";
    private static final String USER_ID = "user-1";
    private static final String ALLOCATION_ID = "alloc-1";

    private AiUsageAllocation pendingAllocation;

    @BeforeEach
    void setUp() {
        Instant now = Instant.now();
        pendingAllocation = new AiUsageAllocation(
                ALLOCATION_ID, ORG_ID, USER_ID, LocalDate.now(),
                List.of("usage-1", "usage-2"), List.of(),
                AiUsageAllocation.Status.PENDING_VALIDATION, AiUsageAllocation.AssignedBy.AUTO,
                true, 0L, now, now);
    }

    @Test
    void shouldAcceptAllocation_whenPendingValidation() {
        when(allocationRepository.findById(ALLOCATION_ID, ORG_ID))
                .thenReturn(Optional.of(pendingAllocation));
        when(allocationRepository.save(any())).thenReturn(pendingAllocation);

        useCase.execute(ORG_ID, USER_ID, ALLOCATION_ID);

        verify(allocationRepository).save(pendingAllocation);
        verify(allocationRepository).updateAllocationIdOnUsageRecords(
                eq(List.of("usage-1", "usage-2")), eq(ALLOCATION_ID));
    }

    @Test
    void shouldThrowNotFound_whenAllocationDoesNotExist() {
        when(allocationRepository.findById(ALLOCATION_ID, ORG_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(ORG_ID, USER_ID, ALLOCATION_ID))
                .isInstanceOf(AllocationNotFoundException.class)
                .hasMessageContaining(ALLOCATION_ID);

        verify(allocationRepository, never()).save(any());
    }

    @Test
    void shouldThrowForbidden_whenUserIsNotOwner() {
        when(allocationRepository.findById(ALLOCATION_ID, ORG_ID))
                .thenReturn(Optional.of(pendingAllocation));

        assertThatThrownBy(() -> useCase.execute(ORG_ID, "other-user", ALLOCATION_ID))
                .isInstanceOf(ForbiddenOperationException.class);

        verify(allocationRepository, never()).save(any());
    }

    @Test
    void shouldThrowAlreadyValidated_whenStatusIsValidated() {
        Instant now = Instant.now();
        AiUsageAllocation validatedAllocation = new AiUsageAllocation(
                ALLOCATION_ID, ORG_ID, USER_ID, LocalDate.now(),
                List.of("usage-1"), List.of(),
                AiUsageAllocation.Status.VALIDATED, AiUsageAllocation.AssignedBy.AUTO,
                true, 0L, now, now);

        when(allocationRepository.findById(ALLOCATION_ID, ORG_ID))
                .thenReturn(Optional.of(validatedAllocation));

        assertThatThrownBy(() -> useCase.execute(ORG_ID, USER_ID, ALLOCATION_ID))
                .isInstanceOf(AllocationAlreadyValidatedException.class);

        verify(allocationRepository, never()).save(any());
    }

    @Test
    void shouldRecalculateEveryProjectOfTheSplits_withOrganizationScopedLookup() {
        pendingAllocation.setSplits(List.of(
                new andromeda.sei.ms.feature.allocation.domain.AllocationSplit("wi-1", "A", new java.math.BigDecimal("0.5000")),
                new andromeda.sei.ms.feature.allocation.domain.AllocationSplit("wi-2", "B", new java.math.BigDecimal("0.5000"))));
        when(allocationRepository.findById(ALLOCATION_ID, ORG_ID)).thenReturn(Optional.of(pendingAllocation));
        when(allocationRepository.save(any())).thenReturn(pendingAllocation);
        when(workItemRepository.findByIdAndOrganizationIdAndActiveTrue("wi-1", ORG_ID))
                .thenReturn(Optional.of(workItem("wi-1", "proj-1")));
        when(workItemRepository.findByIdAndOrganizationIdAndActiveTrue("wi-2", ORG_ID))
                .thenReturn(Optional.of(workItem("wi-2", "proj-2")));

        useCase.execute(ORG_ID, USER_ID, ALLOCATION_ID);

        verify(calculateAiRatiosUseCase).execute(ORG_ID, "proj-1");
        verify(calculateAiRatiosUseCase).execute(ORG_ID, "proj-2");
        verify(workItemRepository, never()).findById(any());
    }

    @Test
    void shouldSkipWorkItemsOutsideTheOrganization_whenRecalculating() {
        pendingAllocation.setSplits(List.of(
                new andromeda.sei.ms.feature.allocation.domain.AllocationSplit("wi-x", "A", java.math.BigDecimal.ONE)));
        when(allocationRepository.findById(ALLOCATION_ID, ORG_ID)).thenReturn(Optional.of(pendingAllocation));
        when(allocationRepository.save(any())).thenReturn(pendingAllocation);
        when(workItemRepository.findByIdAndOrganizationIdAndActiveTrue("wi-x", ORG_ID)).thenReturn(Optional.empty());

        useCase.execute(ORG_ID, USER_ID, ALLOCATION_ID);

        verify(calculateAiRatiosUseCase, never()).execute(any(), any());
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
    void should_rejectAcceptingAgentAllocation() {
        AiUsageAllocation agent = new AiUsageAllocation(ALLOCATION_ID, ORG_ID, USER_ID, LocalDate.now(),
                List.of("usage-1"), List.of(), AiUsageAllocation.Status.VALIDATED,
                AiUsageAllocation.AssignedBy.AGENT, true, 0L, Instant.now(), Instant.now());
        when(allocationRepository.findById(ALLOCATION_ID, ORG_ID)).thenReturn(Optional.of(agent));

        assertThatThrownBy(() -> useCase.execute(ORG_ID, USER_ID, ALLOCATION_ID))
                .isInstanceOf(andromeda.sei.ms.feature.allocation.domain.exception.AllocationAlreadyValidatedException.class);
        verify(allocationRepository, never()).save(any());
    }
}
