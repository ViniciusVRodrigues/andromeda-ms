package andromeda.sei.ms.feature.organization.domain;

import java.util.List;

/**
 * Local-agent settings of the organization (agente-endpoint-v2).
 *
 * @param branchPatterns regexes (group 1 is the task reference) applied to the branch of an agent
 *                       usage row; {@code null} = "never configured", the defaults apply. Never empty.
 */
public record AgentSettings(List<String> branchPatterns) {

    public AgentSettings {
        branchPatterns = branchPatterns == null || branchPatterns.isEmpty() ? null : List.copyOf(branchPatterns);
    }

    public static AgentSettings defaults() {
        return new AgentSettings(null);
    }

    public boolean usingDefaults() {
        return branchPatterns == null;
    }
}
