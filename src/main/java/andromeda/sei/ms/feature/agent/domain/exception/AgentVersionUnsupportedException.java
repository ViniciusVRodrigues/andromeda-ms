package andromeda.sei.ms.feature.agent.domain.exception;

import andromeda.sei.ms.shared.domain.exception.DomainException;

/** Agent below {@code andromeda.agent.min-version} (HTTP 426). */
public class AgentVersionUnsupportedException extends DomainException {

    private final String minVersion;

    public AgentVersionUnsupportedException(String minVersion) {
        super("AGENT_VERSION_UNSUPPORTED", "Versão do agente não suportada; atualize para " + minVersion + " ou superior");
        this.minVersion = minVersion;
    }

    public String getMinVersion() {
        return minVersion;
    }
}
