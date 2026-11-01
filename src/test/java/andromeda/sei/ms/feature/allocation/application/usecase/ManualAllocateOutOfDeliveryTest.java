package andromeda.sei.ms.feature.allocation.application.usecase;

import andromeda.sei.ms.feature.aiusage.domain.AIUsage;
import andromeda.sei.ms.feature.aiusage.domain.AIUsageRepository;
import andromeda.sei.ms.feature.aiusage.domain.AgentUsageQueries;
import andromeda.sei.ms.feature.allocation.application.AllocationTypeEligibility;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocationRepository;
import andromeda.sei.ms.feature.allocation.domain.AllocationSplit;
import andromeda.sei.ms.feature.allocation.domain.exception.AllocationSplitsInvalidException;
import andromeda.sei.ms.feature.workitem.application.usecase.CalculateAiRatiosUseCase;
import andromeda.sei.ms.feature.workitem.domain.WorkItemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** uso-ia-fora-de-entrega P1: the manual path accepts the "Fora de entrega" split and validates its shape. */
@ExtendWith(MockitoExtension.class)
class ManualAllocateOutOfDeliveryTest {

    private static final String ORG = "org-1";
    private static final String USER = "user-1";
    private static final LocalDate DAY = LocalDate.of(2026, 10, 28);

    @Mock private AiUsageAllocationRepository allocationRepository;
    @Mock private AIUsageRepository aiUsageRepository;
    @Mock private WorkItemRepository workItemRepository;
    @Mock private AllocationTypeEligibility typeEligibility;
    @Mock private CalculateAiRatiosUseCase calculateAiRatiosUseCase;
    @Mock private AgentUsageQueries agentUsageQueries;

    @Mock private andromeda.sei.ms.feature.allocation.application.AllocationApprovalService approvalService;

    @org.junit.jupiter.api.BeforeEach
    void approvalIsFree() {
        andromeda.sei.ms.feature.allocation.application.AllocationApprovalTestSupport.stubFree(approvalService);
    }

    @InjectMocks private ManualAllocateUseCase useCase;

    @Test
    void should_saveADayEntirelyOutOfDelivery_withTrimmedNote_andNoWorkItemLookup() {
        AIUsage unallocated = org.mockito.Mockito.mock(AIUsage.class);
        when(unallocated.getId()).thenReturn("u-1");
        when(allocationRepository.findByOrgUserAndDay(ORG, USER, DAY)).thenReturn(List.of());
        when(aiUsageRepository.findUnallocatedByOrganizationIdAndUserIdAndDay(ORG, USER, DAY))
                .thenReturn(List.of(unallocated));
        when(allocationRepository.save(any())).thenAnswer(i -> {
            AiUsageAllocation a = i.getArgument(0);
            a.setId("new-id");
            return a;
        });

        useCase.execute(ORG, USER, null, DAY, List.of(
                AllocationSplit.outOfDelivery(new BigDecimal("1.0000"), AllocationSplit.Category.OTHER, "  reunião  ")));

        ArgumentCaptor<AiUsageAllocation> captor = ArgumentCaptor.forClass(AiUsageAllocation.class);
        verify(allocationRepository).save(captor.capture());
        AllocationSplit saved = captor.getValue().getSplits().get(0);
        assertThat(saved.isOutOfDelivery()).isTrue();
        assertThat(saved.getCategory()).isEqualTo(AllocationSplit.Category.OTHER);
        assertThat(saved.getNote()).isEqualTo("reunião");
        assertThat(captor.getValue().getStatus()).isEqualTo(AiUsageAllocation.Status.VALIDATED);
        verify(workItemRepository, never()).findByIdAndOrganizationIdAndActiveTrue(any(), any());
        verify(calculateAiRatiosUseCase, never()).execute(any(), any());
    }

    @Test
    void should_answer422WithDetail_andWriteNothing_whenOtherHasNoNote() {
        assertThatThrownBy(() -> useCase.execute(ORG, USER, null, DAY, List.of(
                AllocationSplit.outOfDelivery(new BigDecimal("1.0000"), AllocationSplit.Category.OTHER, null))))
                .isInstanceOf(AllocationSplitsInvalidException.class)
                .hasMessageContaining("note is required");

        verify(allocationRepository, never()).save(any());
    }

    @Test
    void should_answer422_whenItemHasNoWorkItemId() {
        assertThatThrownBy(() -> useCase.execute(ORG, USER, null, DAY, List.of(
                new AllocationSplit(null, null, new BigDecimal("1.0000")))))
                .isInstanceOf(AllocationSplitsInvalidException.class)
                .hasMessageContaining("workItemId");
    }

    @Test
    void should_answer422_whenTwoOutOfDeliverySplits() {
        assertThatThrownBy(() -> useCase.execute(ORG, USER, null, DAY, List.of(
                AllocationSplit.outOfDelivery(new BigDecimal("0.5000"), AllocationSplit.Category.STUDY, null),
                AllocationSplit.outOfDelivery(new BigDecimal("0.5000"), AllocationSplit.Category.SUPPORT, null))))
                .isInstanceOf(AllocationSplitsInvalidException.class);
    }
}
