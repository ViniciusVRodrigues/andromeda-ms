package andromeda.sei.ms.feature.dashboard.application;

import andromeda.sei.ms.feature.aiusage.domain.AIUsage;
import andromeda.sei.ms.feature.aiusage.domain.AIUsageRepository;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocationRepository;
import andromeda.sei.ms.feature.allocation.domain.AllocationSplit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class ProjectAiCostCalculatorTest {

    private static final String ORG = "org-1";
    private static final LocalDate FROM = LocalDate.parse("2026-09-01");
    private static final LocalDate TO = LocalDate.parse("2026-09-14");
    private static final Instant NOW = Instant.now();

    @Mock private AiUsageAllocationRepository allocationRepository;
    @Mock private AIUsageRepository aiUsageRepository;

    @InjectMocks
    private ProjectAiCostCalculator calculator;

    @Test
    void should_weightBasisBySplitPercentagesInsideTheProject_when_allocationSpansProjects() {
        AiUsageAllocation split = new AiUsageAllocation("a1", ORG, "user-1", FROM, List.of("u1", "u2"),
                List.of(new AllocationSplit("wi-in", "t", new BigDecimal("0.25")),
                        new AllocationSplit("wi-in-2", "t", new BigDecimal("0.25")),
                        new AllocationSplit("wi-out", "t", new BigDecimal("0.50"))),
                AiUsageAllocation.Status.VALIDATED, AiUsageAllocation.AssignedBy.MANUAL, true, 0L, NOW, NOW);
        when(allocationRepository.findValidatedByOrganizationIdAndDayBetween(ORG, FROM, TO)).thenReturn(List.of(split));
        when(aiUsageRepository.findByOrganizationIdAndIdIn(eq(ORG), eq(Set.of("u1", "u2"))))
                .thenReturn(List.of(usage("u1", "6.00"), usage("u2", "14.00")));

        var cost = calculator.calculate(ORG, Set.of("wi-in", "wi-in-2"), List.of("user-1"), FROM, TO);

        assertThat(cost.allocatedOf("user-1")).isEqualByComparingTo("10.00");
        assertThat(cost.totalAllocated()).isEqualByComparingTo("10.00");
        assertThat(cost.allocatedOf("someone-else")).isEqualByComparingTo("0");
    }

    @Test
    void should_skipAllocationQuery_when_projectHasNoWorkItems() {
        var cost = calculator.calculate(ORG, Set.of(), List.of("user-1"), FROM, TO);

        assertThat(cost.totalAllocated()).isEqualByComparingTo("0");
        verify(allocationRepository, never()).findValidatedByOrganizationIdAndDayBetween(any(), any(), any());
    }

    @Test
    void should_exposeAllocatedUsageRecords_thatReconcileWithTotalAllocated() {
        AiUsageAllocation split = new AiUsageAllocation("a1", ORG, "user-1", FROM, List.of("u1", "u2"),
                List.of(new AllocationSplit("wi-in", "t", new BigDecimal("0.40")),
                        new AllocationSplit("wi-out", "t", new BigDecimal("0.60"))),
                AiUsageAllocation.Status.VALIDATED, AiUsageAllocation.AssignedBy.MANUAL, true, 0L, NOW, NOW);
        when(allocationRepository.findValidatedByOrganizationIdAndDayBetween(ORG, FROM, TO)).thenReturn(List.of(split));
        when(aiUsageRepository.findByOrganizationIdAndIdIn(eq(ORG), eq(Set.of("u1", "u2"))))
                .thenReturn(List.of(usage("u1", "5.00"), usage("u2", "10.00")));

        var records = calculator.allocatedUsage(ORG, Set.of("wi-in"), FROM, TO);
        var total = calculator.calculate(ORG, Set.of("wi-in"), List.of(), FROM, TO).totalAllocated();

        assertThat(records).hasSize(2).allSatisfy(r -> assertThat(r.share()).isEqualByComparingTo("0.40"));
        assertThat(records.stream().map(ProjectAiCostCalculator.AllocatedUsage::allocatedCostUsd)
                .reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo(total).isEqualByComparingTo("6.00");
    }

    private AIUsage usage(String id, String cost) {
        return new AIUsage(id, ORG, "user-1", "gh", "ds", FROM, "gpt", "w", "w", "m", "m",
                10, 10, 1, "vscode", "a1", new BigDecimal(cost), NOW, true, 0L, NOW, NOW);
    }
}
