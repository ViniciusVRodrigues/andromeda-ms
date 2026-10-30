package andromeda.sei.ms.feature.aiusage.application;

import andromeda.sei.ms.feature.aiusage.domain.AiefOverlapRepository.AgentCoverage;
import andromeda.sei.ms.feature.aiusage.domain.AiefOverlapRepository.Candidate;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AiefOverlapRuleTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 5);
    private final AiefEditorTools tools = AiefEditorTools.defaults();

    private static Candidate row(String id, String editor, boolean active, String supersededBy) {
        return new Candidate(id, "u1", DAY, editor, active, supersededBy);
    }

    private AiefOverlapRule.Decision decide(List<Candidate> rows, Set<AgentCoverage> cov) {
        return AiefOverlapRule.decide(rows, cov, tools);
    }

    @Test
    void should_deactivate_when_activeExtensionRowIsCoveredByTheSameTool() {
        var d = decide(List.of(row("a", "Claude Code", true, null)),
                Set.of(new AgentCoverage("u1", DAY, "claude-code")));
        assertThat(d.toDeactivate()).containsExactly("a");
        assertThat(d.toReactivate()).isEmpty();
    }

    @Test
    void should_keepActive_when_theAgentSentAnotherTool() {
        var d = decide(List.of(row("a", "Codex CLI", true, null)),
                Set.of(new AgentCoverage("u1", DAY, "claude-code")));
        assertThat(d.toDeactivate()).isEmpty();
    }

    @Test
    void should_keepActive_when_theEditorHasNoTool() {
        var d = decide(List.of(row("a", "VS Code", true, null)),
                Set.of(new AgentCoverage("u1", DAY, "claude-code")));
        assertThat(d.toDeactivate()).isEmpty();
        assertThat(d.toReactivate()).isEmpty();
    }

    @Test
    void should_keepActive_when_otherUserOrDay() {
        var d = decide(List.of(row("a", "Claude Code", true, null)),
                Set.of(new AgentCoverage("u2", DAY, "claude-code"),
                        new AgentCoverage("u1", DAY.plusDays(1), "claude-code")));
        assertThat(d.toDeactivate()).isEmpty();
    }

    @Test
    void should_reactivate_when_supersededRowIsNoLongerCovered() {
        var d = decide(List.of(row("a", "Claude Code", false, "AGENT")), Set.of());
        assertThat(d.toReactivate()).containsExactly("a");
        assertThat(d.toDeactivate()).isEmpty();
    }

    @Test
    void should_keepOff_when_supersededRowIsStillCovered() {
        var d = decide(List.of(row("a", "Claude Code", false, "AGENT")),
                Set.of(new AgentCoverage("u1", DAY, "claude-code")));
        assertThat(d.toReactivate()).isEmpty();
        assertThat(d.toDeactivate()).isEmpty();
    }

    @Test
    void should_notTouch_when_inactiveForAnotherReason() {
        var d = decide(List.of(row("a", "Claude Code", false, null), row("b", "Claude Code", false, "OTHER")),
                Set.of());
        assertThat(d.toReactivate()).isEmpty();
        assertThat(d.toDeactivate()).isEmpty();
    }
}
