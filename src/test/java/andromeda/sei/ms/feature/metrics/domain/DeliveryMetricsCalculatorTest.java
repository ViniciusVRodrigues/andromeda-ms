package andromeda.sei.ms.feature.metrics.domain;

import andromeda.sei.ms.feature.organization.domain.RemediationRules;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryMetricsCalculatorTest {

    private static final Instant FROM = Instant.parse("2026-09-01T00:00:00Z");
    private static final Instant TO = FROM.plus(Duration.ofDays(28));
    private static final RemediationRules RULES = RemediationRules.defaults();

    // ---- CFR

    @Test
    void should_report15Percent_when_3of20MergesAreBugLinked() {
        List<MergedChange> changes = new ArrayList<>();
        for (int i = 0; i < 20; i++) changes.add(merged("c" + i, "main", i < 3, FROM.plus(Duration.ofDays(1))));

        DeliveryMetrics metrics = DeliveryMetricsCalculator.calculate(changes, List.of(), RULES, FROM, TO);

        assertThat(metrics.method()).isEqualTo(DeliveryMethod.VCS_PROXY);
        assertThat(metrics.changeFailRatePercent()).isEqualTo(15.0);
        assertThat(metrics.changeFailRateClass()).isEqualTo(PerformanceClass.MEDIUM);
        assertThat(metrics.mergedPrs()).isEqualTo(20);
        assertThat(metrics.remediationPrs()).isEqualTo(3);
    }

    @Test
    void should_ignoreMergeIntoNonDefaultBranch_when_countingNumeratorAndDenominator() {
        List<MergedChange> changes = List.of(
                merged("a", "main", false, FROM.plus(Duration.ofDays(1))),
                merged("b", "develop", true, FROM.plus(Duration.ofDays(1))));

        DeliveryMetrics metrics = DeliveryMetricsCalculator.calculate(changes, List.of(), RULES, FROM, TO);

        assertThat(metrics.mergedPrs()).isEqualTo(1);
        assertThat(metrics.remediationPrs()).isZero();
        assertThat(metrics.changeFailRatePercent()).isEqualTo(0.0);
    }

    @Test
    void should_countOnlyMergesIntoTheProductionBranch_when3MergeIntoDevelopAnd1IntoMain() {
        Instant at = FROM.plus(Duration.ofDays(1));
        List<MergedChange> changes = List.of(
                production("d1", "develop", "main", at), production("d2", "develop", "main", at),
                production("d3", "develop", "main", at), production("m1", "main", "main", at));

        DeliveryMetrics metrics = DeliveryMetricsCalculator.calculate(changes, List.of(), RULES, FROM, TO);

        assertThat(metrics.mergedPrs()).isEqualTo(1);
    }

    @Test
    void should_followAProductionBranchOtherThanTheDefault() {
        Instant at = FROM.plus(Duration.ofDays(1));
        List<MergedChange> changes = List.of(
                production("d1", "develop", "develop", at), production("m1", "main", "develop", at));

        assertThat(DeliveryMetricsCalculator.calculate(changes, List.of(), RULES, FROM, TO).mergedPrs()).isEqualTo(1);
    }

    @Test
    void should_countUnknownBranches_when_legacyRowsHaveNoBranchData() {
        MergedChange legacy = new MergedChange("l", "x", null, null, null, false, FROM, null, FROM.plus(Duration.ofDays(1)));

        DeliveryMetrics metrics = DeliveryMetricsCalculator.calculate(List.of(legacy), List.of(), RULES, FROM, TO);

        assertThat(metrics.method()).isEqualTo(DeliveryMethod.VCS_PROXY);
        assertThat(metrics.mergedPrs()).isEqualTo(1);
    }

    @Test
    void should_ignoreMergesOutsideWindow_when_windowIsHalfOpen() {
        List<MergedChange> changes = List.of(
                merged("before", "main", false, FROM.minusSeconds(1)),
                merged("atEnd", "main", false, TO),
                merged("atStart", "main", false, FROM));

        DeliveryMetrics metrics = DeliveryMetricsCalculator.calculate(changes, List.of(), RULES, FROM, TO);

        assertThat(metrics.mergedPrs()).isEqualTo(1);
    }

    @Test
    void should_classifyTwentyPercentAsLow_when_graded() {
        assertThat(DeliveryMetricsCalculator.classifyChangeFailRate(20.0)).isEqualTo(PerformanceClass.LOW);
        assertThat(DeliveryMetricsCalculator.classifyChangeFailRate(5.0)).isEqualTo(PerformanceClass.ELITE);
        assertThat(DeliveryMetricsCalculator.classifyChangeFailRate(10.0)).isEqualTo(PerformanceClass.HIGH);
        assertThat(DeliveryMetricsCalculator.classifyChangeFailRate(null)).isNull();
    }

    // ---- deploy frequency / lead time

    @Test
    void should_report3DeploysPerWeek_when_12MergesIn28Days() {
        List<MergedChange> changes = new ArrayList<>();
        for (int i = 0; i < 12; i++) changes.add(merged("c" + i, "main", false, FROM.plus(Duration.ofDays(i + 1))));

        DeliveryMetrics metrics = DeliveryMetricsCalculator.calculate(changes, List.of(), RULES, FROM, TO);

        assertThat(metrics.deploymentsPerWeek()).isEqualTo(3.0);
        assertThat(metrics.deploymentFrequencyClass()).isEqualTo(PerformanceClass.HIGH);
    }

    @Test
    void should_useAtLeastOneWeek_when_windowIsShorter() {
        List<MergedChange> changes = List.of(merged("a", "main", false, FROM.plus(Duration.ofHours(1))),
                merged("b", "main", false, FROM.plus(Duration.ofHours(2))));

        DeliveryMetrics metrics = DeliveryMetricsCalculator.calculate(changes, List.of(), RULES, FROM,
                FROM.plus(Duration.ofDays(2)));

        assertThat(metrics.deploymentsPerWeek()).isEqualTo(2.0);
    }

    @Test
    void should_measureLeadTimeFromEarliestCommit_when_commitDataAvailable() {
        Instant mergedAt = FROM.plus(Duration.ofDays(3));
        MergedChange change = new MergedChange("a", "x", "f", "main", "main", false,
                mergedAt.minus(Duration.ofHours(5)), mergedAt.minus(Duration.ofHours(30)), mergedAt);

        DeliveryMetrics metrics = DeliveryMetricsCalculator.calculate(List.of(change), List.of(), RULES, FROM, TO);

        assertThat(metrics.changeLeadTime().p50()).isEqualTo(30.0);
        assertThat(metrics.changeLeadTimeClass()).isEqualTo(PerformanceClass.MEDIUM);
    }

    @Test
    void should_fallBackToCreatedAt_when_commitDataMissing() {
        Instant mergedAt = FROM.plus(Duration.ofDays(3));
        MergedChange change = new MergedChange("a", "x", "f", "main", "main", false,
                mergedAt.minus(Duration.ofHours(5)), null, mergedAt);

        DeliveryMetrics metrics = DeliveryMetricsCalculator.calculate(List.of(change), List.of(), RULES, FROM, TO);

        assertThat(metrics.changeLeadTime().p50()).isEqualTo(5.0);
        assertThat(metrics.changeLeadTime().n()).isEqualTo(1);
    }

    // ---- fallback / unavailable

    @Test
    void should_useBoardsProxy_when_noMergesButCompletedItems() {
        List<CompletedWorkItem> items = List.of(
                new CompletedWorkItem("1", true, FROM.plus(Duration.ofDays(1)), 10.0),
                new CompletedWorkItem("2", false, FROM.plus(Duration.ofDays(2)), 20.0),
                new CompletedWorkItem("3", false, FROM.plus(Duration.ofDays(3)), 30.0),
                new CompletedWorkItem("4", false, FROM.plus(Duration.ofDays(4)), null));

        DeliveryMetrics metrics = DeliveryMetricsCalculator.calculate(List.of(), items, RULES, FROM, TO);

        assertThat(metrics.method()).isEqualTo(DeliveryMethod.BOARDS_PROXY);
        assertThat(metrics.changeFailRatePercent()).isEqualTo(25.0);
        assertThat(metrics.deploymentsPerWeek()).isEqualTo(1.0);
        assertThat(metrics.changeLeadTime().p50()).isEqualTo(20.0);
        assertThat(metrics.changeLeadTime().n()).isEqualTo(3);
        assertThat(metrics.mergedPrs()).isZero();
    }

    @Test
    void should_returnUnavailableWithNullClasses_when_noData() {
        DeliveryMetrics metrics = DeliveryMetricsCalculator.calculate(List.of(), List.of(), RULES, FROM, TO);

        assertThat(metrics.method()).isEqualTo(DeliveryMethod.UNAVAILABLE);
        assertThat(metrics.changeFailRatePercent()).isNull();
        assertThat(metrics.changeFailRateClass()).isNull();
        assertThat(metrics.deploymentsPerWeek()).isNull();
        assertThat(metrics.deploymentFrequencyClass()).isNull();
        assertThat(metrics.changeLeadTime().n()).isZero();
        assertThat(metrics.changeLeadTimeClass()).isNull();
    }

    @Test
    void should_preferVcs_when_bothSourcesHaveData() {
        DeliveryMetrics metrics = DeliveryMetricsCalculator.calculate(
                List.of(merged("a", "main", false, FROM.plus(Duration.ofDays(1)))),
                List.of(new CompletedWorkItem("1", true, FROM.plus(Duration.ofDays(1)), 1.0)),
                RULES, FROM, TO);

        assertThat(metrics.method()).isEqualTo(DeliveryMethod.VCS_PROXY);
        assertThat(metrics.changeFailRatePercent()).isEqualTo(0.0);
    }

    private MergedChange production(String id, String baseBranch, String productionBranch, Instant mergedAt) {
        return new MergedChange(id, "Change " + id, "feature/" + id, baseBranch, productionBranch, false,
                mergedAt.minus(Duration.ofHours(2)), null, mergedAt);
    }

    private MergedChange merged(String id, String baseBranch, boolean bug, Instant mergedAt) {
        return new MergedChange(id, "Change " + id, "feature/" + id, baseBranch, "main", bug,
                mergedAt.minus(Duration.ofHours(2)), null, mergedAt);
    }
}
