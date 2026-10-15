package andromeda.sei.ms.feature.usage.presentation;

import andromeda.sei.ms.feature.usage.application.usecase.RecordUsageEventsUseCase;
import andromeda.sei.ms.feature.usage.domain.exception.UsageEventsRateLimitedException;
import andromeda.sei.ms.feature.usage.presentation.dto.UsageEventsRequest;
import andromeda.sei.ms.shared.infrastructure.ratelimit.RateLimiter;
import andromeda.sei.ms.shared.infrastructure.security.AndromedaUserDetails;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * Usage-event ingestion. Any authenticated user; no {@code projectId}, so it is outside the
 * project-scoped endpoint rule. The identity of every event is the token's user and the tenant's
 * organization; the body cannot override them.
 */
@RestController
@RequestMapping("/api/v1/usage-events")
@RequiredArgsConstructor
public class UsageEventController {

    static final int MAX_REQUESTS = 120;
    static final Duration WINDOW = Duration.ofMinutes(10);

    private final RecordUsageEventsUseCase recordUsageEventsUseCase;
    private final RateLimiter rateLimiter;

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> record(@Valid @RequestBody UsageEventsRequest request,
                                       @AuthenticationPrincipal AndromedaUserDetails user) {
        String key = "usage-events:" + user.getUserId();
        if (!rateLimiter.tryAcquire(key, MAX_REQUESTS, WINDOW)) {
            throw new UsageEventsRateLimitedException(rateLimiter.retryAfterSeconds(key, WINDOW));
        }
        recordUsageEventsUseCase.execute(
                TenantContextHolder.get(),
                user.getUserId(),
                request.events().stream().map(e -> e.toDraft()).toList());
        return ResponseEntity.accepted().build();
    }
}
