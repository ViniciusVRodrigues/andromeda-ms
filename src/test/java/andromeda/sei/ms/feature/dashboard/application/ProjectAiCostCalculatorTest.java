package andromeda.sei.ms.feature.dashboard.application;

import andromeda.sei.ms.feature.aiusage.domain.AIUsageRepository;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocationRepository;
import andromeda.sei.ms.feature.allocation.domain.AllocationCoverage;
import andromeda.sei.ms.feature.allocation.domain.AllocationCoverageDetail;
import andromeda.sei.ms.feature.allocation.domain.AllocationSplit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectAiCostCalculatorTest {

    private static final String ORG = "org-1";
    private static final LocalDate FROM = LocalDate.parse("2026-09-01");
    private static final LocalDate TO = LocalDate.parse("2026-09-14");

    @Mock private AiUsageAllocationRepository allocationRepository;
    @Mock private AIUsageRepository aiUsageRepository;

    @InjectMocks
    private ProjectAiCostCalculator calculator;

    @Test
    void should_weightBasisBySplitPercentagesInsideTheProject_when_allocationSpansProjects() {
        AllocationCoverage split = new AllocationCoverage("a1", "user-1", FROM,
                List.of(new AllocationSplit("wi-in", "t", new BigDecimal("0.25")),
                        new AllocationSplit("wi-in-2", "t", new BigDecimal("0.25")),
                        new AllocationSplit("wi-out", "t", new BigDecimal("0.50"))),
                new BigDecimal("20.00"), 40L);
        // the query is scoped by the project's work items; the split share still decides the cost
        when(allocationRepository.summarizeValidatedCoverage(ORG, Set.of("wi-in", "wi-in-2"), FROM, TO))
                .thenReturn(List.of(split));

        var cost = calculator.calculate(ORG, Set.of("wi-in", "wi-in-2"), List.of("user-1"), FROM, TO);

        assertThat(cost.allocatedOf("user-1")).isEqualByComparingTo("10.00");
        assertThat(cost.totalAllocated()).isEqualByComparingTo("10.00");
        assertThat(cost.allocatedTokens()).isEqualTo(20L);
        assertThat(cost.allocatedOf("someone-else")).isEqualByComparingTo("0");
    }

    @Test
    void should_skipAllocationQuery_when_projectHasNoWorkItems() {
        var cost = calculator.calculate(ORG, Set.of(), List.of("user-1"), FROM, TO);

        assertThat(cost.totalAllocated()).isEqualByComparingTo("0");
        assertThat(cost.allocatedTokens()).isNull();
        verify(allocationRepository, never()).summarizeValidatedCoverage(any(), any(), any(), any());
    }

    @Test
    void should_exposeAllocatedUsageRecords_thatReconcileWithTotalAllocated() {
        List<AllocationSplit> splits = List.of(new AllocationSplit("wi-in", "t", new BigDecimal("0.40")),
                new AllocationSplit("wi-out", "t", new BigDecimal("0.60")));
        when(allocationRepository.summarizeValidatedCoverage(ORG, Set.of("wi-in"), FROM, TO))
                .thenReturn(List.of(new AllocationCoverage("a1", "user-1", FROM, splits, new BigDecimal("15.00"), 40L)));
        when(allocationRepository.summarizeValidatedCoverageByDayAndModel(ORG, Set.of("wi-in"), FROM, TO))
                .thenReturn(List.of(
                        new AllocationCoverageDetail("a1", "user-1", splits, FROM, "gpt", 10, 10, 1, new BigDecimal("5.00")),
                        new AllocationCoverageDetail("a1", "user-1", splits, FROM, "claude", 10, 10, 1,
                                new BigDecimal("10.00"))));

        var records = calculator.allocatedUsage(ORG, Set.of("wi-in"), FROM, TO);
        var total = calculator.calculate(ORG, Set.of("wi-in"), List.of(), FROM, TO).totalAllocated();

        assertThat(records).hasSize(2).allSatisfy(r -> assertThat(r.share()).isEqualByComparingTo("0.40"));
        assertThat(records.stream().map(ProjectAiCostCalculator.AllocatedUsage::allocatedCostUsd)
                .reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo(total).isEqualByComparingTo("6.00");
    }
}
