package andromeda.sei.ms.feature.agent.presentation;

import andromeda.sei.ms.feature.agent.application.dto.AgentDtos.ApprovedDeviceOutput;
import andromeda.sei.ms.feature.agent.application.dto.AgentDtos.PairingOutput;
import andromeda.sei.ms.feature.agent.application.usecase.ApprovePairingUseCase;
import andromeda.sei.ms.feature.agent.application.usecase.DenyPairingUseCase;
import andromeda.sei.ms.feature.agent.application.usecase.GetPairingUseCase;
import andromeda.sei.ms.feature.agent.domain.exception.AgentRateLimitedException;
import andromeda.sei.ms.shared.infrastructure.ratelimit.RateLimiter;
import andromeda.sei.ms.shared.infrastructure.security.AndromedaUserDetails;
import andromeda.sei.ms.shared.infrastructure.web.ClientIpResolver;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/** The logged-in person looks at, approves or denies the pairing the agent asked for. */
@Tag(name = "Agent pairing")
@RestController
@RequestMapping("/api/v1/agent/pairing")
@PreAuthorize("hasAuthority('CONNECTOR_TOKEN_MANAGE_OWN')")
public class AgentPairingController {

    static final int LIMIT = 10;
    static final Duration WINDOW = Duration.ofMinutes(1);

    private final GetPairingUseCase getPairing;
    private final ApprovePairingUseCase approvePairing;
    private final DenyPairingUseCase denyPairing;
    private final RateLimiter rateLimiter;
    private final ClientIpResolver clientIpResolver;

    public AgentPairingController(GetPairingUseCase getPairing, ApprovePairingUseCase approvePairing,
                                  DenyPairingUseCase denyPairing, RateLimiter rateLimiter,
                                  ClientIpResolver clientIpResolver) {
        this.getPairing = getPairing;
        this.approvePairing = approvePairing;
        this.denyPairing = denyPairing;
        this.rateLimiter = rateLimiter;
        this.clientIpResolver = clientIpResolver;
    }

    @GetMapping("/{userCode}")
    public PairingOutput get(@AuthenticationPrincipal AndromedaUserDetails user, @PathVariable String userCode,
                             HttpServletRequest request) {
        limit(user);
        return getPairing.execute(user.getOrganizationId(), user.getUserId(), userCode,
                clientIpResolver.resolve(request));
    }

    @PostMapping("/{userCode}/approve")
    public ApprovedDeviceOutput approve(@AuthenticationPrincipal AndromedaUserDetails user,
                                        @PathVariable String userCode) {
        limit(user);
        return approvePairing.execute(user.getOrganizationId(), user.getUserId(), userCode);
    }

    @PostMapping("/{userCode}/deny")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deny(@AuthenticationPrincipal AndromedaUserDetails user, @PathVariable String userCode) {
        limit(user);
        denyPairing.execute(user.getOrganizationId(), user.getUserId(), userCode);
    }

    private void limit(AndromedaUserDetails user) {
        String key = "agent-pairing:" + user.getUserId();
        if (!rateLimiter.tryAcquire(key, LIMIT, WINDOW)) {
            throw new AgentRateLimitedException(rateLimiter.retryAfterSeconds(key, WINDOW));
        }
    }
}
