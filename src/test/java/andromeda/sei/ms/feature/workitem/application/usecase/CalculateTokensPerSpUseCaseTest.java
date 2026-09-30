package andromeda.sei.ms.feature.workitem.application.usecase;

import andromeda.sei.ms.feature.aiusage.domain.AIUsage;
import andromeda.sei.ms.feature.aiusage.domain.AIUsageRepository;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocationRepository;
import andromeda.sei.ms.feature.allocation.domain.AllocationSplit;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.domain.WorkItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CalculateTokensPerSpUseCaseTest {

    @Mock WorkItemRepository workItemRepository;
    @Mock AiUsageAllocationRepository allocationRepository;
    @Mock AIUsageRepository aiUsageRepository;

    CalculateTokensPerSpUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CalculateTokensPerSpUseCase(workItemRepository, allocationRepository, aiUsageRepository);
    }

    private WorkItem workItem(String id, Double effort) {
        return new WorkItem(id, "org-1", "proj-1", WorkItem.Source.AZURE_DEVOPS,
                "ext-" + id, null, "Title", null, null, null, WorkItem.ResolvedStatus.DONE,
                null, effort, null, "sprint-1", null, "user-1", null, null,
                Instant.now(), null, null, null, null, null, true, null, Instant.now(), null, null, null, null, effort, null, null, null);
    }

    private AIUsage aiUsage(String id, String userId, LocalDate day, long inputTokens, long outputTokens) {
        return new AIUsage(id, "org-1", userId, null, "ds-1", day, "gpt-4o",
                "ws-1", null, "m-1", null, inputTokens, outputTokens, 1, null,
                null, null, Instant.now(), true, null, Instant.now(), Instant.now());
    }

    private AiUsageAllocation allocation(String workItemId, String userId, LocalDate day,
                                          BigDecimal percentage, List<String> usageIds) {
        List<AllocationSplit> splits = List.of(new AllocationSplit(workItemId, "Title", percentage));
        return new AiUsageAllocation(null, "org-1", userId, day,
                usageIds, splits,
                AiUsageAllocation.Status.VALIDATED, AiUsageAllocation.AssignedBy.AUTO,
                true, null, Instant.now(), Instant.now());
    }

    @Test
    void should_setTokensPerSpToNull_when_noAllocations() {
        WorkItem wi = workItem("wi-1", 3.0);
        when(workItemRepository.findAllByOrganizationIdAndProjectId("org-1", "proj-1")).thenReturn(List.of(wi));
        when(allocationRepository.findValidatedByOrganizationIdAndWorkItemIdIn("org-1", Set.of("wi-1")))
                .thenReturn(List.of());

        useCase.execute("org-1", "proj-1");

        verify(workItemRepository).updateTokensPerSp("wi-1", null);
    }

    @Test
    void should_skipWorkItem_when_noEffort() {
        WorkItem wi = workItem("wi-1", null);
        when(workItemRepository.findAllByOrganizationIdAndProjectId("org-1", "proj-1")).thenReturn(List.of(wi));

        useCase.execute("org-1", "proj-1");

        verify(workItemRepository, never()).updateTokensPerSp(anyString(), any());
        verify(allocationRepository, never()).findValidatedByOrganizationIdAndWorkItemIdIn(any(), any());
    }

    @Test
    void should_computeTokensPerSp_when_singleAllocation() {
        WorkItem wi = workItem("wi-1", 2.0);
        LocalDate day = LocalDate.of(2026, 6, 1);
        when(workItemRepository.findAllByOrganizationIdAndProjectId("org-1", "proj-1")).thenReturn(List.of(wi));
        when(allocationRepository.findValidatedByOrganizationIdAndWorkItemIdIn("org-1", Set.of("wi-1")))
                .thenReturn(List.of(allocation("wi-1", "user-1", day, BigDecimal.ONE, List.of("usage-1"))));
        when(aiUsageRepository.findByOrganizationIdAndIdIn("org-1", Set.of("usage-1")))
                .thenReturn(List.of(aiUsage("usage-1", "user-1", day, 1000L, 500L)));

        useCase.execute("org-1", "proj-1");

        // (1000 + 500) * 1.0 / 2.0 = 750.0
        verify(workItemRepository).updateTokensPerSp("wi-1", 750.0);
    }

    @Test
    void should_computeTokensPerSp_when_partialPercentage() {
        WorkItem wi = workItem("wi-1", 4.0);
        LocalDate day = LocalDate.of(2026, 6, 1);
        when(workItemRepository.findAllByOrganizationIdAndProjectId("org-1", "proj-1")).thenReturn(List.of(wi));
        when(allocationRepository.findValidatedByOrganizationIdAndWorkItemIdIn("org-1", Set.of("wi-1")))
                .thenReturn(List.of(allocation("wi-1", "user-1", day, new BigDecimal("0.5"), List.of("usage-1"))));
        when(aiUsageRepository.findByOrganizationIdAndIdIn("org-1", Set.of("usage-1")))
                .thenReturn(List.of(aiUsage("usage-1", "user-1", day, 2000L, 0L)));

        useCase.execute("org-1", "proj-1");

        // 2000 * 0.5 / 4.0 = 250.0
        verify(workItemRepository).updateTokensPerSp("wi-1", 250.0);
    }

    @Test
    void should_loadAllocationsAndUsagesOnce_when_projectHasManyItems() {
        WorkItem wi1 = workItem("wi-1", 2.0);
        WorkItem wi2 = workItem("wi-2", 1.0);
        LocalDate day1 = LocalDate.of(2026, 6, 1);
        LocalDate day2 = LocalDate.of(2026, 6, 2);
        when(workItemRepository.findAllByOrganizationIdAndProjectId("org-1", "proj-1")).thenReturn(List.of(wi1, wi2));
        when(allocationRepository.findValidatedByOrganizationIdAndWorkItemIdIn("org-1", Set.of("wi-1", "wi-2")))
                .thenReturn(List.of(
                        allocation("wi-1", "user-1", day1, BigDecimal.ONE, List.of("usage-1")),
                        allocation("wi-1", "user-1", day2, BigDecimal.ONE, List.of("usage-2")),
                        allocation("wi-2", "user-1", day2, BigDecimal.ONE, List.of("usage-2"))));
        when(aiUsageRepository.findByOrganizationIdAndIdIn(eq("org-1"), any()))
                .thenReturn(List.of(aiUsage("usage-1", "user-1", day1, 500L, 500L),
                        aiUsage("usage-2", "user-1", day2, 300L, 200L)));

        useCase.execute("org-1", "proj-1");

        // wi-1: (1000 + 500) / 2.0 = 750.0 ; wi-2: 500 / 1.0 = 500.0
        verify(workItemRepository).updateTokensPerSp("wi-1", 750.0);
        verify(workItemRepository).updateTokensPerSp("wi-2", 500.0);
        verify(allocationRepository, times(1)).findValidatedByOrganizationIdAndWorkItemIdIn(any(), any());
        verify(aiUsageRepository, times(1)).findByOrganizationIdAndIdIn(any(), any());
    }
}
