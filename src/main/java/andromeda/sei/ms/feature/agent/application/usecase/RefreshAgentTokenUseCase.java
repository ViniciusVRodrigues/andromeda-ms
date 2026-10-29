package andromeda.sei.ms.feature.agent.application.usecase;

import andromeda.sei.ms.feature.agent.application.dto.AgentDtos.TokenPairOutput;
import andromeda.sei.ms.feature.agent.domain.AgentAuthorities;
import andromeda.sei.ms.feature.agent.domain.AgentCodes;
import andromeda.sei.ms.feature.agent.domain.AgentDevice;
import andromeda.sei.ms.feature.agent.domain.AgentDeviceRepository;
import andromeda.sei.ms.feature.agent.domain.exception.AgentOAuthException;
import andromeda.sei.ms.feature.organization.domain.OrganizationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/**
 * Rotating refresh. The refresh replaced by the last rotation is accepted once inside the grace
 * window (the agent may have died before saving the new pair); any later reuse of an old refresh
 * revokes the device, a sign of a leaked token.
 */
@Component
public class RefreshAgentTokenUseCase {

    private static final int ATTEMPTS = 2;
    static final String REUSE_REVOKER = "system:refresh-reuse";

    private final AgentDeviceRepository devices;
    private final OrganizationRepository organizations;
    private final AgentTokenIssuer tokenIssuer;
    private final Clock clock;

    @Autowired
    public RefreshAgentTokenUseCase(AgentDeviceRepository devices, OrganizationRepository organizations,
                                    AgentTokenIssuer tokenIssuer) {
        this(devices, organizations, tokenIssuer, Clock.systemUTC());
    }

    RefreshAgentTokenUseCase(AgentDeviceRepository devices, OrganizationRepository organizations,
                             AgentTokenIssuer tokenIssuer, Clock clock) {
        this.devices = devices;
        this.organizations = organizations;
        this.tokenIssuer = tokenIssuer;
        this.clock = clock;
    }

    public TokenPairOutput execute(String refreshToken) {
        if (refreshToken == null || !refreshToken.startsWith(AgentCodes.REFRESH_TOKEN_PREFIX)) {
            throw invalidGrant();
        }
        String hash = AgentCodes.sha256(refreshToken);
        for (int attempt = 1; ; attempt++) {
            try {
                return attemptOnce(hash);
            } catch (OptimisticLockingFailureException e) {
                // a concurrent request touched the device; re-read once (it now looks like a grace reuse)
                if (attempt >= ATTEMPTS) {
                    throw invalidGrant();
                }
            }
        }
    }

    private TokenPairOutput attemptOnce(String hash) {
        Instant now = clock.instant();
        Optional<AgentDevice> current = devices.findByRefreshTokenHash(hash);
        if (current.isPresent()) {
            AgentDevice device = current.get();
            requireUsable(device, now);
            if (device.getRefreshTokenExpiresAt() == null || !now.isBefore(device.getRefreshTokenExpiresAt())) {
                throw invalidGrant();
            }
            return result(device, tokenIssuer.issue(device), now);
        }

        AgentDevice device = devices.findByPreviousRefreshHash(hash).orElseThrow(this::invalidGrant);
        requireUsable(device, now);
        if (device.isPreviousRefreshAcceptable(now)) {
            return result(device, tokenIssuer.reissueAfterGrace(device), now);
        }
        device.revoke(REUSE_REVOKER, now);
        device.setUpdatedAt(now);
        devices.save(device);
        throw invalidGrant();
    }

    private void requireUsable(AgentDevice device, Instant now) {
        if (device.isRevoked()) {
            throw invalidGrant();
        }
    }

    private TokenPairOutput result(AgentDevice device, AgentTokenIssuer.Issued tokens, Instant now) {
        device.setUpdatedAt(now);
        devices.save(device);
        String slug = organizations.findById(device.getOrganizationId()).map(o -> o.getSlug()).orElse(null);
        return new TokenPairOutput(tokens.accessToken(), tokens.refreshToken(), tokens.expiresInSeconds(),
                AgentAuthorities.SCOPE, device.getId(), slug);
    }

    private AgentOAuthException invalidGrant() {
        return new AgentOAuthException("invalid_grant", "Refresh token inválido, expirado ou revogado");
    }
}
