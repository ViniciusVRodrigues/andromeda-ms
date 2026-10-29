package andromeda.sei.ms.feature.organization.infrastructure.persistence;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Subdocumento embutido em {@code organizations.agentSettings}; {@code branchPatterns} nulo = defaults. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentSettingsDocument {

    private List<String> branchPatterns;
}
