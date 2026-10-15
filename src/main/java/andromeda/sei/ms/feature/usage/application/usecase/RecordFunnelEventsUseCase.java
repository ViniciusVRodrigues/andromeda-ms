package andromeda.sei.ms.feature.usage.application.usecase;

import andromeda.sei.ms.feature.usage.application.FunnelEventQuota;
import andromeda.sei.ms.feature.usage.application.TelemetrySettings;
import andromeda.sei.ms.feature.usage.domain.FunnelEvent;
import andromeda.sei.ms.feature.usage.domain.FunnelEventDraft;
import andromeda.sei.ms.feature.usage.domain.FunnelEventRepository;
import andromeda.sei.ms.feature.usage.domain.FunnelFlow;
import andromeda.sei.ms.feature.usage.domain.exception.FunnelEventsRateLimitedException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Stores a batch of anonymous funnel events. Collection off, or the instance's daily ceiling reached:
 * the batch is silently dropped (the endpoint still answers 202). A funnel over its daily budget is a 429.
 * All-or-nothing: an incoherent event rejects the batch before anything is written.
 */
@Component
@RequiredArgsConstructor
public class RecordFunnelEventsUseCase {

    private final TelemetrySettings settings;
    private final FunnelEventQuota quota;
    private final FunnelEventRepository repository;

    public void execute(String funnelId, FunnelFlow flow, List<FunnelEventDraft> drafts) {
        execute(funnelId, flow, drafts, Instant.now());
    }

    void execute(String funnelId, FunnelFlow flow, List<FunnelEventDraft> drafts, Instant receivedAt) {
        if (!settings.isEnabled()) return;
        List<FunnelEvent> events = drafts.stream()
                .map(d -> FunnelEvent.fromClient(funnelId, flow, d, receivedAt))
                .toList();
        switch (quota.admit(funnelId, events.size())) {
            case GLOBAL_CEILING -> { }
            case FUNNEL_LIMIT -> throw new FunnelEventsRateLimitedException(quota.secondsUntilReset());
            case ADMITTED -> repository.insertAll(events);
        }
    }
}
