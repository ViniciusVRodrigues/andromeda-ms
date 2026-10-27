package andromeda.sei.ms.feature.allocation.application.usecase;

import andromeda.sei.ms.feature.aiusage.domain.AIUsage;
import andromeda.sei.ms.feature.aiusage.domain.AIUsageRepository;
import andromeda.sei.ms.feature.allocation.application.dto.AllocationEntryInput;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocationRepository;
import andromeda.sei.ms.feature.allocation.domain.AllocationSplit;
import andromeda.sei.ms.feature.allocation.domain.exception.AllocationEmptyException;
import andromeda.sei.ms.feature.allocation.domain.exception.AllocationInvalidSumException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SaveAllocationUseCaseTest {

    private static final String ORG = "org-1";
    private static final String USER = "user-1";
    private static final LocalDate DAY = LocalDate.of(2026, 9, 1);
    private static final Instant NOW = Instant.now();

    @Mock private AiUsageAllocationRepository allocationRepository;
    @Mock private AIUsageRepository aiUsageRepository;
    @Mock private andromeda.sei.ms.feature.workitem.domain.WorkItemRepository workItemRepository;

    @InjectMocks
    private SaveAllocationUseCase useCase;

    @org.junit.jupiter.api.BeforeEach
    void knownWorkItems() {
        for (String id : List.of("wi-1", "wi-2")) {
            org.mockito.Mockito.lenient().when(workItemRepository.findByIdAndOrganizationIdAndActiveTrue(id, ORG))
                    .thenReturn(java.util.Optional.of(org.mockito.Mockito.mock(
                            andromeda.sei.ms.feature.workitem.domain.WorkItem.class)));
        }
    }

    @Test
    void should_rejectWorkItemOfAnotherOrganization_andTouchNothing() {
        when(workItemRepository.findByIdAndOrganizationIdAndActiveTrue("wi-foreign", ORG))
                .thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> useCase.execute(ORG, USER, DAY, List.of(entry("wi-1", "0.5"), entry("wi-foreign", "0.5"))))
                .isInstanceOf(andromeda.sei.ms.feature.workitem.domain.exception.WorkItemNotFoundException.class);

        verify(allocationRepository, never()).deleteByOrganizationIdAndUserIdAndDay(any(), any(), any());
        verify(allocationRepository, never()).save(any());
    }

    @Test
    void should_storeTheDayUsageIdsAndLinkUsagesBack_when_saving() {
        when(aiUsageRepository.findByOrganizationIdAndUserIdAndDayIn(ORG, USER, List.of(DAY)))
                .thenReturn(List.of(usage("u1"), usage("u2")));
        when(allocationRepository.findByOrgUserAndDay(ORG, USER, DAY)).thenReturn(List.of());
        when(allocationRepository.save(any())).thenAnswer(inv -> {
            AiUsageAllocation a = inv.getArgument(0);
            a.setId("alloc-new");
            return a;
        });

        useCase.execute(ORG, USER, DAY, List.of(entry("wi-1", "0.6"), entry("wi-2", "0.4")));

        ArgumentCaptor<AiUsageAllocation> captor = ArgumentCaptor.forClass(AiUsageAllocation.class);
        verify(allocationRepository).save(captor.capture());
        assertThat(captor.getValue().getAiUsageIds()).containsExactly("u1", "u2");
        assertThat(captor.getValue().getStatus()).isEqualTo(AiUsageAllocation.Status.VALIDATED);
        verify(allocationRepository).updateAllocationIdOnUsageRecords(List.of("u1", "u2"), "alloc-new");
    }

    @Test
    void should_clearAllocationIdOfUsagesOnlyTheReplacedAllocationReferenced_when_saving() {
        when(aiUsageRepository.findByOrganizationIdAndUserIdAndDayIn(ORG, USER, List.of(DAY)))
                .thenReturn(List.of(usage("u1")));
        AiUsageAllocation previous = new AiUsageAllocation("alloc-old", ORG, USER, DAY, List.of("u1", "u-gone"),
                List.of(new AllocationSplit("wi-9", "t", BigDecimal.ONE)), AiUsageAllocation.Status.PENDING_VALIDATION,
                AiUsageAllocation.AssignedBy.AUTO, true, 0L, NOW, NOW);
        when(allocationRepository.findByOrgUserAndDay(ORG, USER, DAY)).thenReturn(List.of(previous));
        when(allocationRepository.save(any())).thenAnswer(inv -> {
            AiUsageAllocation a = inv.getArgument(0);
            a.setId("alloc-new");
            return a;
        });

        useCase.execute(ORG, USER, DAY, List.of(entry("wi-1", "1.0")));

        InOrder order = inOrder(allocationRepository);
        order.verify(allocationRepository).deleteByOrganizationIdAndUserIdAndDay(ORG, USER, DAY);
        order.verify(allocationRepository).save(any());
        verify(allocationRepository).updateAllocationIdOnUsageRecords(List.of("u1"), "alloc-new");
        verify(allocationRepository).updateAllocationIdOnUsageRecords(List.of("u-gone"), null);
    }

    @Test
    void should_rejectEmptyEntries_when_nothingToAllocate() {
        assertThatThrownBy(() -> useCase.execute(ORG, USER, DAY, List.of()))
                .isInstanceOf(AllocationEmptyException.class);
        verify(allocationRepository, never()).save(any());
    }

    @Test
    void should_rejectInvalidSum_when_percentagesDoNotAddUpToOne() {
        assertThatThrownBy(() -> useCase.execute(ORG, USER, DAY, List.of(entry("wi-1", "0.5"))))
                .isInstanceOf(AllocationInvalidSumException.class);
        verify(allocationRepository, never()).save(any());
    }

    private AllocationEntryInput entry(String workItemId, String percentage) {
        return new AllocationEntryInput(workItemId, "Title " + workItemId, new BigDecimal(percentage));
    }

    private AIUsage usage(String id) {
        return new AIUsage(id, ORG, USER, "gh", "ds", DAY, "gpt", "w", "w", "m", "m",
                10, 10, 1, "vscode", null, new BigDecimal("1.00"), NOW, true, 0L, NOW, NOW);
    }
}
