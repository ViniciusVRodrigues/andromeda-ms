package andromeda.sei.ms.feature.agent.application.usecase;

import andromeda.sei.ms.feature.agent.domain.SemVer;
import andromeda.sei.ms.feature.agent.domain.exception.AgentVersionUnsupportedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** The minimum and the recommended agent version. A missing version counts as below. */
@Component
public class AgentVersionPolicy {

    /** First agent version that serves the "sync now" request. */
    public static final String SYNC_NOW_MIN_VERSION = "0.3.0";

    public static final String OK = "OK";
    public static final String OUTDATED = "OUTDATED";
    public static final String UNSUPPORTED = "UNSUPPORTED";

    private final String minVersion;
    private final String recommendedVersion;

    public AgentVersionPolicy(@Value("${andromeda.agent.min-version:0.1.0}") String minVersion,
                              @Value("${andromeda.agent.recommended-version:0.1.0}") String recommendedVersion) {
        this.minVersion = minVersion;
        this.recommendedVersion = recommendedVersion;
    }

    public String minVersion() {
        return minVersion;
    }

    public String recommendedVersion() {
        return recommendedVersion;
    }

    public boolean isSupported(String agentVersion) {
        return SemVer.isAtLeast(agentVersion, minVersion);
    }

    public void requireSupported(String agentVersion) {
        if (!isSupported(agentVersion)) {
            throw new AgentVersionUnsupportedException(minVersion);
        }
    }

    /** UNSUPPORTED below the minimum, OUTDATED below the recommended, otherwise OK. */
    public String statusOf(String agentVersion) {
        if (!isSupported(agentVersion)) return UNSUPPORTED;
        return SemVer.isAtLeast(agentVersion, recommendedVersion) ? OK : OUTDATED;
    }
}
