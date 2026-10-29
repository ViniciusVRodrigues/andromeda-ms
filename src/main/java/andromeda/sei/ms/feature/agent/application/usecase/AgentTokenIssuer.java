package andromeda.sei.ms.feature.agent.application.usecase;

import andromeda.sei.ms.feature.agent.domain.AgentCodes;
import andromeda.sei.ms.feature.agent.domain.AgentDevice;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/** Mints a token pair, stores only the hashes on the device and returns the raw values once. */
@Component
public class AgentTokenIssuer {

    public record Issued(String accessToken, String refreshToken, long expiresInSeconds) {
    }

    private final Duration accessTtl;
    private final Duration refreshTtl;
    private final Duration refreshGrace;
    private final Clock clock;

    @Autowired
    public AgentTokenIssuer(@Value("${andromeda.agent.access-token-ttl:PT1H}") Duration accessTtl,
                            @Value("${andromeda.agent.refresh-token-ttl:P90D}") Duration refreshTtl,
                            @Value("${andromeda.agent.refresh-grace:PT2M}") Duration refreshGrace) {
        this(accessTtl, refreshTtl, refreshGrace, Clock.systemUTC());
    }

    AgentTokenIssuer(Duration accessTtl, Duration refreshTtl, Duration refreshGrace, Clock clock) {
        this.accessTtl = accessTtl;
        this.refreshTtl = refreshTtl;
        this.refreshGrace = refreshGrace;
        this.clock = clock;
    }

    /** New pair for the device; a current refresh becomes the previous one (valid once within the grace). */
    public Issued issue(AgentDevice device) {
        String access = AgentCodes.newAccessToken();
        String refresh = AgentCodes.newRefreshToken();
        Instant now = clock.instant();
        device.rotateTokens(AgentCodes.sha256(access), now.plus(accessTtl),
                AgentCodes.sha256(refresh), now.plus(refreshTtl), now.plus(refreshGrace));
        return new Issued(access, refresh, accessTtl.toSeconds());
    }

    /** The previous refresh came back inside the grace: a fresh pair, the previous one now spent. */
    public Issued reissueAfterGrace(AgentDevice device) {
        String access = AgentCodes.newAccessToken();
        String refresh = AgentCodes.newRefreshToken();
        Instant now = clock.instant();
        device.reissueAfterGrace(AgentCodes.sha256(access), now.plus(accessTtl),
                AgentCodes.sha256(refresh), now.plus(refreshTtl));
        return new Issued(access, refresh, accessTtl.toSeconds());
    }
}
