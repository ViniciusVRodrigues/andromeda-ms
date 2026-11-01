package andromeda.sei.ms.feature.dashboard.application;

import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation.Status;
import andromeda.sei.ms.feature.allocation.domain.AllocationDestination;
import andromeda.sei.ms.feature.allocation.domain.AllocationSplit;
import andromeda.sei.ms.feature.allocation.domain.AllocationSplit.Category;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.DashboardV2Parts.AiDestination;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AiDestinationCalculatorTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 2);

    private static AllocationDestination row(String id, String user, Status status, String cost, AllocationSplit... splits) {
        return new AllocationDestination(id, user, DAY, status, List.of(splits), new BigDecimal(cost), 100);
    }

    private static AllocationSplit item(String id, String pct) {
        return new AllocationSplit(id, id, new BigDecimal(pct));
    }

    private static AllocationSplit out(String pct, Category category) {
        return AllocationSplit.outOfDelivery(new BigDecimal(pct), category, null);
    }

    private static BigDecimal sum(AiDestination d) {
        return d.itemsUsd().add(d.otherProjectsUsd()).add(d.outOfDelivery().usd()).add(d.toValidate().usd());
    }

    @Test
    void should_sendThirtyPercentToOutOfDelivery_andKeepTheRestOnItemsAndOtherProjects() {
        AiDestination d = AiDestinationCalculator.compute(
                List.of(row("a1", "u1", Status.VALIDATED, "10.00", item("in", "0.50"), item("elsewhere", "0.20"),
                        out("0.30", Category.STUDY))),
                Set.of("in"), Map.of("u1", new BigDecimal("10.00")), Map.of("u1", "Ana"));

        assertThat(d.itemsUsd()).isEqualByComparingTo("5.00");
        assertThat(d.otherProjectsUsd()).isEqualByComparingTo("2.00");
        assertThat(d.outOfDelivery().usd()).isEqualByComparingTo("3.00");
        assertThat(d.outOfDelivery().byCategory()).singleElement().satisfies(c -> {
            assertThat(c.category()).isEqualTo("STUDY");
            assertThat(c.usd()).isEqualByComparingTo("3.00");
        });
        assertThat(d.outOfDelivery().byPerson()).singleElement().satisfies(p -> {
            assertThat(p.name()).isEqualTo("Ana");
            assertThat(p.sharePct()).isEqualTo(30.0);
        });
        assertThat(d.toValidate().usd()).isEqualByComparingTo("0");
        assertThat(sum(d)).isEqualByComparingTo(d.totalUsd());
    }

    @Test
    void should_countAwaitingAndUnclaimedCostAsToValidate_neverAsDestination() {
        AiDestination d = AiDestinationCalculator.compute(
                List.of(row("a1", "u1", Status.VALIDATED, "6.00", item("in", "1.00")),
                        row("a2", "u1", Status.AWAITING_APPROVAL, "4.00", out("1.00", Category.OTHER))),
                Set.of("in"), Map.of("u1", new BigDecimal("15.00")), Map.of());

        assertThat(d.itemsUsd()).isEqualByComparingTo("6.00");
        assertThat(d.outOfDelivery().usd()).isEqualByComparingTo("0");
        assertThat(d.outOfDelivery().byPerson()).isEmpty();
        // 15 total - 6 validated: 4 awaiting approval + 5 pending or not allocated
        assertThat(d.toValidate().usd()).isEqualByComparingTo("9.00");
        assertThat(d.toValidate().awaitingUsd()).isEqualByComparingTo("4.00");
        assertThat(sum(d)).isEqualByComparingTo(d.totalUsd());
    }

    @Test
    void should_closeOnTheTotal_whenSplitsRoundInEveryBucket() {
        AiDestination d = AiDestinationCalculator.compute(
                List.of(row("a1", "u1", Status.VALIDATED, "0.333333", item("in", "0.3333"), item("x", "0.3333"),
                                out("0.3334", Category.SUPPORT)),
                        row("a2", "u2", Status.VALIDATED, "0.777777", item("in", "0.1"),
                                out("0.9", Category.DOCS_MEETING))),
                Set.of("in"), Map.of("u1", new BigDecimal("1.234567"), "u2", new BigDecimal("0.777777")), Map.of());

        assertThat(sum(d)).isEqualByComparingTo(d.totalUsd());
        assertThat(d.totalUsd()).isEqualByComparingTo("2.012344");
        assertThat(d.otherProjectsUsd().signum()).isGreaterThanOrEqualTo(0);
        assertThat(d.outOfDelivery().byCategory()).extracting(c -> c.category())
                .containsExactlyInAnyOrder("SUPPORT", "DOCS_MEETING");
        assertThat(d.outOfDelivery().byPerson()).extracting(p -> p.userId()).containsExactly("u2", "u1");
    }

    @Test
    void should_beAllZero_withoutCost() {
        AiDestination d = AiDestinationCalculator.compute(List.of(), Set.of(), Map.of(), Map.of());

        assertThat(d.totalUsd()).isEqualByComparingTo("0");
        assertThat(d.toValidate().usd()).isEqualByComparingTo("0");
        assertThat(d.outOfDelivery().byCategory()).isEmpty();
    }
}
