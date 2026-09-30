package andromeda.sei.ms.feature.metrics.domain;

import andromeda.sei.ms.feature.organization.domain.RemediationRules;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RemediationClassifierTest {

    private static final RemediationRules DEFAULTS = RemediationRules.defaults();

    @Test
    void should_countBugLinkedChange_when_ruleEnabled() {
        assertThat(RemediationClassifier.isRemediation(change("Fix", "feature/x", true), DEFAULTS)).isTrue();
    }

    @Test
    void should_notCountBugLinkedChange_when_ruleDisabled() {
        RemediationRules rules = new RemediationRules(List.of("hotfix/"), false, true);

        assertThat(RemediationClassifier.isRemediation(change("Fix", "feature/x", true), rules)).isFalse();
    }

    @Test
    void should_countRevert_when_titleStartsWithRevertCaseInsensitive() {
        assertThat(RemediationClassifier.isRemediation(change("Revert \"Add export\"", "revert-1", false), DEFAULTS))
                .isTrue();
        assertThat(RemediationClassifier.isRemediation(change("revert login", "x", false), DEFAULTS)).isTrue();
        assertThat(RemediationClassifier.isRemediation(change("Reverting nothing", "x", false), DEFAULTS)).isFalse();
    }

    @Test
    void should_notCountRevert_when_ruleDisabled() {
        RemediationRules rules = new RemediationRules(List.of(), true, false);

        assertThat(RemediationClassifier.isRemediation(change("Revert x", "x", false), rules)).isFalse();
    }

    @Test
    void should_countDefaultHotfixPrefix_when_branchStartsWithIt() {
        assertThat(RemediationClassifier.isRemediation(change("Patch", "hotfix/login", false), DEFAULTS)).isTrue();
        assertThat(RemediationClassifier.isRemediation(change("Patch", "feature/hotfix/login", false), DEFAULTS))
                .isFalse();
    }

    @Test
    void should_countCustomPrefix_when_configured() {
        RemediationRules rules = new RemediationRules(List.of("fix/prod-"), true, true);

        assertThat(RemediationClassifier.isRemediation(change("Patch", "fix/prod-123", false), rules)).isTrue();
        assertThat(RemediationClassifier.isRemediation(change("Patch", "hotfix/x", false), rules)).isFalse();
    }

    @Test
    void should_notCount_when_noRuleMatches() {
        assertThat(RemediationClassifier.isRemediation(change("Add export", "feature/export", false), DEFAULTS))
                .isFalse();
        assertThat(RemediationClassifier.isRemediation(change(null, null, false), DEFAULTS)).isFalse();
    }

    private MergedChange change(String title, String sourceBranch, boolean linkedToBug) {
        Instant now = Instant.parse("2026-09-01T00:00:00Z");
        return new MergedChange("c1", title, sourceBranch, "main", "main", linkedToBug, now, null, now);
    }
}
