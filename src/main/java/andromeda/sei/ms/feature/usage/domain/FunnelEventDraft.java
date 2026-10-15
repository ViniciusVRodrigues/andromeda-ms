package andromeda.sei.ms.feature.usage.domain;

import java.time.Instant;

/** One funnel event as the client reported it, before validation. */
public record FunnelEventDraft(FunnelStage stage, Instant occurredAt, String step, Long visibleMs, String value,
                               String viewport) {
}
