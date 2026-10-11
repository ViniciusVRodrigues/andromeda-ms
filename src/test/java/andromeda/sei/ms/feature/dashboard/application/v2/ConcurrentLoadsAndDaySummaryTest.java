package andromeda.sei.ms.feature.dashboard.application.v2;

import andromeda.sei.ms.feature.aiusage.domain.AIUsage;
import andromeda.sei.ms.feature.dashboard.application.AiUsageSummaries;
import andromeda.sei.ms.feature.project.domain.exception.ProjectNotFoundException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * dashboards-customizaveis (B1 performance) — concurrent reads rethrow a read's own exception (a
 * {@code DomainException} keeps its type, so it keeps its HTTP status); the allocation summary from
 * per-day sums classifies exactly like the one from records.
 */
class ConcurrentLoadsAndDaySummaryTest {

    private static final Instant NOW = Instant.parse("2026-10-11T12:00:00Z");

    @Test
    void should_combineDependentReads() {
        try (ConcurrentLoads loads = new ConcurrentLoads()) {
            CompletableFuture<Integer> first = loads.supply(() -> 20);
            CompletableFuture<Integer> second = loads.then(first, n -> n + 1);

            assertThat(ConcurrentLoads.join(second)).isEqualTo(21);
        }
    }

    @Test
    void should_rethrowTheReadsOwnException_unwrapped() {
        try (ConcurrentLoads loads = new ConcurrentLoads()) {
            CompletableFuture<Object> failing = loads.supply(() -> { throw new ProjectNotFoundException("p9"); });

            assertThatThrownBy(() -> ConcurrentLoads.join(failing)).isInstanceOf(ProjectNotFoundException.class);
        }
    }

    @Test
    void should_classifyPersonDays_likeTheRecordBasedSummary() {
        List<AIUsage> records = List.of(
                usage("a", "2026-10-01", "al-1"), usage("a", "2026-10-01", null),   // claimed day
                usage("a", "2026-10-02", null),                                    // unallocated day
                usage("b", "2026-10-01", null),                                    // validated (by set)
                usage("b", "2026-10-03", "al-9"));                                 // pending
        Set<String> validated = Set.of(DashboardV2Views.personDay("b", LocalDate.parse("2026-10-01")));

        assertThat(DashboardV2Views.allocationSummaryOfDays(AiUsageSummaries.of(records), validated))
                .isEqualTo(DashboardV2Views.allocationSummary(records, validated));
    }

    private static AIUsage usage(String userId, String day, String allocationId) {
        return new AIUsage(userId + day + allocationId, "org-1", userId, "gh", "ds", LocalDate.parse(day), "m", "w", "w",
                "m", "m", 1, 1, 1, "v", allocationId, BigDecimal.ONE, NOW, true, 0L, NOW, NOW);
    }
}
