package andromeda.sei.ms.feature.allocation.application.usecase;

import andromeda.sei.ms.feature.aiusage.domain.AIUsage;
import andromeda.sei.ms.feature.aiusage.domain.AIUsageRepository;
import andromeda.sei.ms.feature.allocation.application.dto.PendingBatchOutput;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocationRepository;
import andromeda.sei.ms.feature.allocation.domain.AllocationSplit;
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

    private GetPendingAllocationBatchesUseCase useCase;

    private static final String ORG = "org-1";
    private static final String USER = "user-1";
    private static final LocalDate DAY = LocalDate.of(2026, 6, 3);

    @BeforeEach
    void setUp() {
        useCase = new GetPendingAllocationBatchesUseCase(aiUsageRepository, allocationRepository);
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
