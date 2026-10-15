package andromeda.sei.ms.feature.usage.application.usecase;

import andromeda.sei.ms.feature.usage.application.TelemetrySettings;
import andromeda.sei.ms.feature.usage.domain.FunnelEvent;
import andromeda.sei.ms.feature.usage.domain.FunnelEventRepository;
import andromeda.sei.ms.feature.usage.domain.FunnelFlow;
import andromeda.sei.ms.feature.usage.domain.FunnelRules;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * When signup or invite succeeds, ties the still-anonymous events of the client's funnel to the new
 * account and records {@code COMPLETE}. Best-effort: absent or malformed {@code funnelId}, collection
 * off, or any failure (a WARN with no e-mail and no IP) never reaches the caller.
 */
@Component
@RequiredArgsConstructor
public class LinkFunnelUseCase {

    private static final Logger log = LoggerFactory.getLogger(LinkFunnelUseCase.class);

    private final FunnelEventRepository repository;
    private final TelemetrySettings settings;

    public void execute(String funnelId, FunnelFlow flow, String organizationId, String userId) {
        if (!FunnelRules.isFunnelId(funnelId) || !settings.isEnabled()) return;
        Instant now = Instant.now();
        try {
            repository.linkToAccount(funnelId, flow, organizationId, userId, now.minus(FunnelRules.LINK_WINDOW), now);
            repository.insertAll(List.of(FunnelEvent.complete(funnelId, flow, organizationId, userId, now)));
        } catch (RuntimeException e) {
            log.warn("Could not link {} funnel (org {}, user {}): {}", flow.value(), organizationId, userId,
                    e.getClass().getSimpleName());
        }
    }
}
