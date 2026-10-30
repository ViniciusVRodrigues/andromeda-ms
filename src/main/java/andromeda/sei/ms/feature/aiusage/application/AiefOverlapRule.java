package andromeda.sei.ms.feature.aiusage.application;

import andromeda.sei.ms.feature.aiusage.domain.AiefOverlapRepository.AgentCoverage;
import andromeda.sei.ms.feature.aiusage.domain.AiefOverlapRepository.Candidate;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Pure rule: the agent wins over the extension for the same user, day and tool. */
public final class AiefOverlapRule {

    public static final String AGENT = "AGENT";

    public record Decision(List<String> toDeactivate, List<String> toReactivate) {}

    private AiefOverlapRule() {}

    public static Decision decide(List<Candidate> candidates, Set<AgentCoverage> coverage, AiefEditorTools tools) {
        List<String> off = new ArrayList<>();
        List<String> on = new ArrayList<>();
        for (Candidate c : candidates) {
            String tool = tools.toolOf(c.editor());
            if (tool == null) continue;
            boolean covered = coverage.contains(new AgentCoverage(c.userId(), c.day(), tool));
            if (c.active() && covered) {
                off.add(c.id());
            } else if (!c.active() && AGENT.equals(c.supersededBy()) && !covered) {
                on.add(c.id());
            }
        }
        return new Decision(off, on);
    }
}
