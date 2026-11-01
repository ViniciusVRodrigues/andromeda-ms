package andromeda.sei.ms.feature.allocation.application;

import andromeda.sei.ms.feature.allocation.application.AllocationApprovalPolicy.CapContext;
import andromeda.sei.ms.feature.allocation.application.AllocationApprovalPolicy.Decision;
import andromeda.sei.ms.feature.allocation.application.AllocationApprovalPolicy.Input;
import andromeda.sei.ms.feature.allocation.application.AllocationApprovalPolicy.ItemRef;
import andromeda.sei.ms.feature.allocation.application.AllocationApprovalPolicy.UsageRow;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation.ApprovalReason;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation.ApprovalReasonCode;
import andromeda.sei.ms.feature.allocation.domain.AllocationSplit;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AllocationApprovalPolicyTest {

    private static final Map<String, String> NAMES = Map.of("pX", "Projeto X", "pY", "Projeto Y");
    private static final Map<String, ItemRef> ITEMS = Map.of(
            "wiX", new ItemRef("pX", "AB#1"),
            "wiY", new ItemRef("pY", "AB#2"),
            "wiNone", new ItemRef(null, "AB#3"));

    private static BigDecimal d(String v) {
        return new BigDecimal(v);
    }

    private static AllocationSplit item(String id, String pct) {
        return new AllocationSplit(id, id, d(pct));
    }

    private static AllocationSplit out(String pct) {
        return AllocationSplit.outOfDelivery(d(pct), AllocationSplit.Category.STUDY, null);
    }

    private static UsageRow row(String repo, String project, String cost) {
        return new UsageRow(repo, project, d(cost), 100);
    }

    private static Decision evaluate(List<UsageRow> rows, List<AllocationSplit> splits, CapContext cap) {
        return AllocationApprovalPolicy.evaluate(new Input(rows, splits, ITEMS, NAMES, List.of("pOwn"), cap));
    }

    // ---- REPO_OTHER_PROJECT ------------------------------------------------------------------

    @Test
    void repositoryOfProjectXOnItemOfProjectYAsksForApproval() {
        Decision decision = evaluate(List.of(row("org/ms", "pX", "10")), List.of(item("wiY", "1.0000")), null);

        assertThat(decision.needsApproval()).isTrue();
        assertThat(decision.reasons()).hasSize(1);
        ApprovalReason reason = decision.reasons().get(0);
        assertThat(reason.code()).isEqualTo(ApprovalReasonCode.REPO_OTHER_PROJECT);
        assertThat(reason.repository()).isEqualTo("org/ms");
        assertThat(reason.projectId()).isEqualTo("pX");
        assertThat(reason.projectName()).isEqualTo("Projeto X");
        assertThat(reason.workItemId()).isEqualTo("wiY");
        assertThat(reason.displayId()).isEqualTo("AB#2");
        assertThat(decision.approverProjectIds()).containsExactly("pX");
    }

    @Test
    void repositoryOfProjectXOnItemOfProjectXIsFree() {
        Decision decision = evaluate(List.of(row("org/ms", "pX", "10")), List.of(item("wiX", "1.0000")), null);

        assertThat(decision.needsApproval()).isFalse();
        assertThat(decision.approverProjectIds()).isEmpty();
    }

    @Test
    void partWithoutRepositoryCoversTheForeignShare() {
        // 40% of the cost has no project repository: up to 40% may go anywhere.
        List<UsageRow> rows = List.of(row("org/ms", "pX", "6"), row(null, null, "4"));

        assertThat(evaluate(rows, List.of(item("wiX", "0.6000"), item("wiY", "0.4000")), null)
                .needsApproval()).isFalse();
        // the same rows with 50% going to Y: 0.50 > 0.40 + 0.01
        assertThat(evaluate(rows, List.of(item("wiX", "0.5000"), item("wiY", "0.5000")), null)
                .needsApproval()).isTrue();
    }

    @Test
    void shareToleranceIsOneHundredth() {
        List<UsageRow> rows = List.of(row("org/ms", "pX", "6"), row(null, null, "4"));

        // 0.4100 = 0.40 + 0.01: still free
        assertThat(evaluate(rows, List.of(item("wiX", "0.5900"), item("wiY", "0.4100")), null)
                .needsApproval()).isFalse();
        // 0.4200 is over
        assertThat(evaluate(rows, List.of(item("wiX", "0.5800"), item("wiY", "0.4200")), null)
                .needsApproval()).isTrue();
    }

    @Test
    void rowsWithoutProjectRepositoryAreAlwaysFree() {
        assertThat(evaluate(List.of(row(null, null, "10")), List.of(item("wiY", "1.0000")), null)
                .needsApproval()).isFalse();
        // repository that has no project (unbound or ambiguous) comes as a null project too
        assertThat(evaluate(List.of(row("org/unbound", null, "10")), List.of(out("1.0000")), null)
                .needsApproval()).isFalse();
    }

    @Test
    void itemWithoutProjectIsForeignWhenTheRowsHaveARepositoryProject() {
        Decision decision = evaluate(List.of(row("org/ms", "pX", "10")), List.of(item("wiNone", "1.0000")), null);

        assertThat(decision.reasons()).hasSize(1);
        assertThat(decision.reasons().get(0).workItemId()).isEqualTo("wiNone");
    }

    @Test
    void outOfDeliveryBucketOnRepositoryRowsIsForeignWithoutItem() {
        Decision decision = evaluate(List.of(row("org/ms", "pX", "10")),
                List.of(item("wiX", "0.5000"), out("0.5000")), null);

        assertThat(decision.reasons()).hasSize(1);
        ApprovalReason reason = decision.reasons().get(0);
        assertThat(reason.code()).isEqualTo(ApprovalReasonCode.REPO_OTHER_PROJECT);
        assertThat(reason.workItemId()).isNull();
        assertThat(reason.displayId()).isNull();
        assertThat(reason.projectId()).isEqualTo("pX");
    }

    @Test
    void oneReasonPerForeignSplitWithTheProjectOfTheBiggestRepositoryShare() {
        List<UsageRow> rows = List.of(row("org/a", "pX", "2"), row("org/b", "pY", "8"));

        Decision decision = evaluate(rows, List.of(item("wiNone", "0.5000"), out("0.5000")), null);

        assertThat(decision.reasons()).hasSize(2);
        assertThat(decision.reasons()).allSatisfy(r -> {
            assertThat(r.projectId()).isEqualTo("pY");
            assertThat(r.repository()).isEqualTo("org/b");
        });
        assertThat(decision.approverProjectIds()).containsExactly("pX", "pY");
    }

    @Test
    void sharesFallBackToTokensWhenTheCostIsZero() {
        List<UsageRow> rows = List.of(new UsageRow("org/ms", "pX", BigDecimal.ZERO, 900),
                new UsageRow(null, null, BigDecimal.ZERO, 100));

        // free share 0.10: 0.30 to Y is foreign
        assertThat(evaluate(rows, List.of(item("wiX", "0.7000"), item("wiY", "0.3000")), null)
                .needsApproval()).isTrue();
        assertThat(evaluate(rows, List.of(item("wiX", "0.9000"), item("wiY", "0.1000")), null)
                .needsApproval()).isFalse();
    }

    // ---- OUT_OF_DELIVERY_OVER_CAP ------------------------------------------------------------

    @Test
    void underTheCapIsFree() {
        // base 100, cap 15% = 15; used 5 + this 10 = 15: not over
        Decision decision = evaluate(List.of(row(null, null, "10")), List.of(out("1.0000")),
                new CapContext(15, d("100"), "2026-09", d("5")));

        assertThat(decision.needsApproval()).isFalse();
    }

    @Test
    void overTheCapAsksForApprovalWithTheNumbers() {
        Decision decision = evaluate(List.of(row(null, null, "10")), List.of(item("wiY", "0.5000"), out("0.5000")),
                new CapContext(15, d("120"), "2026-09", d("16.40")));

        assertThat(decision.reasons()).hasSize(1);
        ApprovalReason reason = decision.reasons().get(0);
        assertThat(reason.code()).isEqualTo(ApprovalReasonCode.OUT_OF_DELIVERY_OVER_CAP);
        assertThat(reason.capPercent()).isEqualTo(15);
        assertThat(reason.baseUsd()).isEqualByComparingTo("120.00");
        assertThat(reason.baseMonth()).isEqualTo("2026-09");
        assertThat(reason.usedUsd()).isEqualByComparingTo("16.40");
        assertThat(reason.thisUsd()).isEqualByComparingTo("5.00");
        // (16.40 + 5.00) / 120 = 17.83% -> 18
        assertThat(reason.afterPercent()).isEqualTo(18);
        assertThat(decision.approverProjectIds()).containsExactly("pOwn");
    }

    @Test
    void usedCountsWhatIsAlreadyAwaitingBecauseTheServiceFeedsItIn() {
        // used 14 (validated 9 + awaiting 5) + this 2 = 16 > 15
        Decision decision = evaluate(List.of(row(null, null, "2")), List.of(out("1.0000")),
                new CapContext(15, d("100"), "2026-09", d("14")));

        assertThat(decision.needsApproval()).isTrue();
    }

    @Test
    void capZeroSendsEveryOutOfDeliveryToApproval() {
        Decision decision = evaluate(List.of(row(null, null, "1")), List.of(out("1.0000")),
                new CapContext(0, d("100"), "2026-09", d("0")));

        assertThat(decision.needsApproval()).isTrue();
    }

    @Test
    void capHundredStillGuardsTheBase() {
        CapContext cap = new CapContext(100, d("50"), "2026-09", d("45"));

        assertThat(evaluate(List.of(row(null, null, "5")), List.of(out("1.0000")), cap).needsApproval()).isFalse();
        assertThat(evaluate(List.of(row(null, null, "6")), List.of(out("1.0000")), cap).needsApproval()).isTrue();
    }

    @Test
    void zeroBaseWithCostOverTheCapAsksWithoutPercent() {
        Decision decision = evaluate(List.of(row(null, null, "3")), List.of(out("1.0000")),
                new CapContext(15, BigDecimal.ZERO, "2026-10", BigDecimal.ZERO));

        assertThat(decision.reasons()).hasSize(1);
        assertThat(decision.reasons().get(0).afterPercent()).isNull();
    }

    @Test
    void noOutOfDeliverySplitMeansNoCapReason() {
        Decision decision = evaluate(List.of(row(null, null, "999")), List.of(item("wiY", "1.0000")),
                new CapContext(0, BigDecimal.ZERO, "2026-10", BigDecimal.ZERO));

        assertThat(decision.needsApproval()).isFalse();
    }

    @Test
    void bothReasonsMergeTheApprovers() {
        Decision decision = evaluate(List.of(row("org/ms", "pX", "10")), List.of(out("1.0000")),
                new CapContext(15, d("10"), "2026-09", d("0")));

        assertThat(decision.reasons()).extracting(ApprovalReason::code)
                .containsExactly(ApprovalReasonCode.REPO_OTHER_PROJECT, ApprovalReasonCode.OUT_OF_DELIVERY_OVER_CAP);
        assertThat(decision.approverProjectIds()).containsExactly("pX", "pOwn");
    }

    // ---- applyTo -----------------------------------------------------------------------------

    @Test
    void applyToStampsAwaitingApprovalOrValidates() {
        Instant now = Instant.parse("2026-10-31T12:00:00Z");
        AiUsageAllocation a = new AiUsageAllocation(null, "o", "u", LocalDate.of(2026, 10, 30), List.of("r"),
                List.of(), AiUsageAllocation.Status.PENDING_VALIDATION, AiUsageAllocation.AssignedBy.MANUAL,
                true, null, now, now);

        Decision.free().applyTo(a, now);
        assertThat(a.getStatus()).isEqualTo(AiUsageAllocation.Status.VALIDATED);
        assertThat(a.getApproval()).isNull();

        Decision decision = evaluate(List.of(row("org/ms", "pX", "10")), List.of(item("wiY", "1.0000")), null);
        decision.applyTo(a, now);
        assertThat(a.getStatus()).isEqualTo(AiUsageAllocation.Status.AWAITING_APPROVAL);
        assertThat(a.getApproval().requestedAt()).isEqualTo(now);
        assertThat(a.getApproval().reasons()).hasSize(1);
        assertThat(a.getApproval().approverProjectIds()).containsExactly("pX");
        assertThat(a.getApproval().decision()).isNull();
        assertThat(a.closesTheDay()).isFalse();
    }
}
