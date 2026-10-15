package andromeda.sei.ms.feature.usage.domain;

import java.time.Instant;

/**
 * One funnel event. Anonymous ({@code organizationId} and {@code userId} null) until the flow ends and
 * {@link FunnelEventRepository#linkToAccount} points it at the new account. No IP, e-mail, name or typed
 * text has a field here.
 */
public final class FunnelEvent {

    private final String funnelId;
    private final FunnelFlow flow;
    private final FunnelStage stage;
    private final String step;
    private final Long visibleMs;
    private final String value;
    private final String viewport;
    private final Instant occurredAt;
    private final String organizationId;
    private final String userId;
    private final Instant linkedAt;
    private final Instant createdAt;

    private FunnelEvent(String funnelId, FunnelFlow flow, FunnelStage stage, String step, Long visibleMs,
                        String value, String viewport, Instant occurredAt, String organizationId, String userId,
                        Instant linkedAt, Instant createdAt) {
        this.funnelId = funnelId;
        this.flow = flow;
        this.stage = stage;
        this.step = step;
        this.visibleMs = visibleMs;
        this.value = value;
        this.viewport = viewport;
        this.occurredAt = occurredAt;
        this.organizationId = organizationId;
        this.userId = userId;
        this.linkedAt = linkedAt;
        this.createdAt = createdAt;
    }

    /** Anonymous event from the client: validated, trimmed to the fields of its stage, clock fixed. */
    public static FunnelEvent fromClient(String funnelId, FunnelFlow flow, FunnelEventDraft d, Instant receivedAt) {
        if (!FunnelRules.isFunnelId(funnelId)) throw new FunnelEventInvalidException("funnelId inválido");
        String violation = FunnelRules.violation(flow, d);
        if (violation != null) throw new FunnelEventInvalidException(violation);
        return new FunnelEvent(funnelId, flow, d.stage(), d.step(),
                d.stage() == FunnelStage.STEP_TIME ? d.visibleMs() : null,
                d.value(), d.viewport(), withinWindow(d.occurredAt(), receivedAt), null, null, null, receivedAt);
    }

    /** Server-side end of the flow, already tied to the new account. */
    public static FunnelEvent complete(String funnelId, FunnelFlow flow, String organizationId, String userId,
                                       Instant at) {
        return new FunnelEvent(funnelId, flow, FunnelStage.COMPLETE, null, null, null, null, at,
                organizationId, userId, at, at);
    }

    /** Rebuilds a stored event (read side of the export). */
    public static FunnelEvent restore(String funnelId, FunnelFlow flow, FunnelStage stage, String step,
                                      Long visibleMs, String value, String viewport, Instant occurredAt,
                                      String organizationId, String userId, Instant linkedAt, Instant createdAt) {
        return new FunnelEvent(funnelId, flow, stage, step, visibleMs, value, viewport, occurredAt,
                organizationId, userId, linkedAt, createdAt);
    }

    private static Instant withinWindow(Instant occurredAt, Instant receivedAt) {
        if (occurredAt == null
                || occurredAt.isBefore(receivedAt.minus(UsageEventRules.CLOCK_PAST_TOLERANCE))
                || occurredAt.isAfter(receivedAt.plus(UsageEventRules.CLOCK_FUTURE_TOLERANCE))) {
            return receivedAt;
        }
        return occurredAt;
    }

    public String getFunnelId() { return funnelId; }
    public FunnelFlow getFlow() { return flow; }
    public FunnelStage getStage() { return stage; }
    public String getStep() { return step; }
    public Long getVisibleMs() { return visibleMs; }
    public String getValue() { return value; }
    public String getViewport() { return viewport; }
    public Instant getOccurredAt() { return occurredAt; }
    public String getOrganizationId() { return organizationId; }
    public String getUserId() { return userId; }
    public Instant getLinkedAt() { return linkedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
