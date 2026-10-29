package andromeda.sei.ms.feature.agent.application.usecase;

import andromeda.sei.ms.feature.agent.application.dto.AgentDtos.TokenPairOutput;
import andromeda.sei.ms.feature.agent.domain.AgentAuthorities;
import andromeda.sei.ms.feature.agent.domain.AgentCodes;
import andromeda.sei.ms.feature.agent.domain.AgentDevice;
import andromeda.sei.ms.feature.agent.domain.AgentDeviceAuthorization;
import andromeda.sei.ms.feature.agent.domain.AgentDeviceAuthorization.Status;
import andromeda.sei.ms.feature.agent.domain.AgentDeviceAuthorizationRepository;
import andromeda.sei.ms.feature.agent.domain.AgentDeviceRepository;
import andromeda.sei.ms.feature.agent.domain.exception.AgentOAuthException;
import andromeda.sei.ms.feature.organization.domain.OrganizationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

/** Device-code grant of RFC 8628: pending, slow_down, denied, expired and a single exchange. */
@Component
public class ExchangeDeviceTokenUseCase {

    static final int SLOW_DOWN_STEP_SECONDS = 5;

    private final AgentDeviceAuthorizationRepository authorizations;
    private final AgentDeviceRepository devices;
    private final OrganizationRepository organizations;
    private final AgentTokenIssuer tokenIssuer;
    private final Clock clock;

    @Autowired
    public ExchangeDeviceTokenUseCase(AgentDeviceAuthorizationRepository authorizations,
                                      AgentDeviceRepository devices, OrganizationRepository organizations,
                                      AgentTokenIssuer tokenIssuer) {
        this(authorizations, devices, organizations, tokenIssuer, Clock.systemUTC());
    }

    ExchangeDeviceTokenUseCase(AgentDeviceAuthorizationRepository authorizations, AgentDeviceRepository devices,
                               OrganizationRepository organizations, AgentTokenIssuer tokenIssuer, Clock clock) {
        this.authorizations = authorizations;
        this.devices = devices;
        this.organizations = organizations;
        this.tokenIssuer = tokenIssuer;
        this.clock = clock;
    }

    public TokenPairOutput execute(String deviceCode) {
        if (deviceCode == null || !deviceCode.startsWith(AgentCodes.DEVICE_CODE_PREFIX)) {
            throw new AgentOAuthException("invalid_grant", "Código do dispositivo desconhecido");
        }
        AgentDeviceAuthorization a = authorizations.findByDeviceCodeHash(AgentCodes.sha256(deviceCode))
                .orElseThrow(() -> new AgentOAuthException("invalid_grant", "Código do dispositivo desconhecido"));
        Instant now = clock.instant();

        if (a.getStatus() == Status.CONSUMED) {
            throw new AgentOAuthException("expired_token", "O código já foi trocado");
        }
        if (a.getStatus() == Status.DENIED) {
            throw new AgentOAuthException("access_denied", "O pareamento foi negado");
        }
        if (a.isExpired(now)) {
            discardUnclaimedDevice(a, now);
            throw new AgentOAuthException("expired_token", "O código expirou");
        }
        if (a.getStatus() == Status.PENDING) {
            throw pending(a, now);
        }
        return issue(a, now);
    }

    private AgentOAuthException pending(AgentDeviceAuthorization a, Instant now) {
        boolean tooFast = a.getLastPolledAt() != null
                && now.isBefore(a.getLastPolledAt().plusSeconds(a.getIntervalSeconds()));
        if (tooFast) {
            a.setIntervalSeconds(a.getIntervalSeconds() + SLOW_DOWN_STEP_SECONDS);
        }
        a.setLastPolledAt(now);
        a.setUpdatedAt(now);
        try {
            authorizations.save(a);
        } catch (OptimisticLockingFailureException ignored) {
            // another poll or the approval won the write; the answer below is still valid
        }
        return tooFast
                ? new AgentOAuthException("slow_down", "Consulte menos vezes; novo intervalo: " + a.getIntervalSeconds() + " s")
                : new AgentOAuthException("authorization_pending", "Aguardando a aprovação");
    }

    private TokenPairOutput issue(AgentDeviceAuthorization a, Instant now) {
        AgentDevice device = devices.findActiveByIdAndOrganizationId(a.getDeviceId(), a.getOrganizationId())
                .orElseThrow(() -> new AgentOAuthException("expired_token", "O dispositivo não existe mais"));

        a.setStatus(Status.CONSUMED);
        a.setUpdatedAt(now);
        try {
            authorizations.save(a); // optimistic lock: of two concurrent exchanges only one gets here
        } catch (OptimisticLockingFailureException e) {
            throw new AgentOAuthException("expired_token", "O código já foi trocado");
        }

        AgentTokenIssuer.Issued tokens = tokenIssuer.issue(device);
        device.setUpdatedAt(now);
        devices.save(device);

        String slug = organizations.findById(device.getOrganizationId())
                .map(o -> o.getSlug()).orElse(null);
        return new TokenPairOutput(tokens.accessToken(), tokens.refreshToken(), tokens.expiresInSeconds(),
                AgentAuthorities.SCOPE, device.getId(), slug);
    }

    /** Approved but never claimed before the code expired: the device row would be an active orphan. */
    private void discardUnclaimedDevice(AgentDeviceAuthorization a, Instant now) {
        if (a.getStatus() != Status.APPROVED || a.getDeviceId() == null) {
            return;
        }
        devices.findActiveByIdAndOrganizationId(a.getDeviceId(), a.getOrganizationId()).ifPresent(d -> {
            if (d.getAccessTokenHash() == null) {
                d.revoke(a.getUserId(), now);
                devices.save(d);
            }
        });
    }
}
