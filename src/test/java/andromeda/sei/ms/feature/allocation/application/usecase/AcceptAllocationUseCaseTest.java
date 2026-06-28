package andromeda.sei.ms.feature.allocation.application.usecase;

import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocationRepository;
import andromeda.sei.ms.feature.allocation.domain.exception.AllocationAlreadyValidatedException;
import andromeda.sei.ms.feature.allocation.domain.exception.AllocationNotFoundException;
import andromeda.sei.ms.feature.workitem.application.usecase.CalculateTokensPerSpUseCase;
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
    private CalculateTokensPerSpUseCase calculateTokensPerSpUseCase;

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
}
