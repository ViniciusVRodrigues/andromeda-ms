package andromeda.sei.ms.feature.usage.presentation;

import andromeda.sei.ms.feature.usage.application.usecase.RecordFunnelEventsUseCase;
import andromeda.sei.ms.feature.usage.domain.exception.FunnelEventsRateLimitedException;
import andromeda.sei.ms.feature.usage.presentation.dto.FunnelEventsRequest;
import andromeda.sei.ms.shared.infrastructure.ratelimit.RateLimiter;
import andromeda.sei.ms.shared.infrastructure.web.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * Anonymous funnel ingestion (signup and invite steps). Public: listed in {@code SecurityConfig.PUBLIC_ENDPOINTS}.
 * JSON only, which forces a CORS preflight and keeps third-party HTML forms out. No {@code projectId}, so it
 * is outside the project-scoped endpoint rule. The caller's IP is only a rate-limit key: it is never stored
 * or logged.
 */
@RestController
@RequestMapping("/api/v1/usage-events/public")
@RequiredArgsConstructor
public class FunnelEventController {

    static final int MAX_REQUESTS_PER_IP = 120;
    static final Duration WINDOW = Duration.ofMinutes(10);

    private final RecordFunnelEventsUseCase recordFunnelEventsUseCase;
    private final RateLimiter rateLimiter;
    private final ClientIpResolver clientIpResolver;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> record(@Valid @RequestBody FunnelEventsRequest request, HttpServletRequest http) {
        String key = "funnel-events:" + clientIpResolver.resolve(http);
        if (!rateLimiter.tryAcquire(key, MAX_REQUESTS_PER_IP, WINDOW)) {
            throw new FunnelEventsRateLimitedException(rateLimiter.retryAfterSeconds(key, WINDOW));
        }
        recordFunnelEventsUseCase.execute(request.funnelId(), request.flowEnum(), request.drafts());
        return ResponseEntity.accepted().build();
    }
}
