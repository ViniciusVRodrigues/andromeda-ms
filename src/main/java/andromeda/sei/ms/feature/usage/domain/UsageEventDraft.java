package andromeda.sei.ms.feature.usage.domain;

import java.time.Instant;

/**
 * What the client sent for one event, before validation. It has no user or organization on purpose:
 * identity comes from the token, never from the body. Fields the contract does not define (message,
 * stack, URL) do not exist here, so they cannot be persisted.
 */
public record UsageEventDraft(
        UsageEventType type,
        Instant occurredAt,
        String sessionId,
        String viewId,
        String screen,
        String viewport,
        Long visibleMs,
        String element,
        String label,
        String area,
        String action,
        String value,
        String origin,
        Integer status,
        String code) {

    public static Builder builder(UsageEventType type, Instant occurredAt) {
        return new Builder(type, occurredAt);
    }

    public static final class Builder {
        private final UsageEventType type;
        private final Instant occurredAt;
        private String sessionId;
        private String viewId;
        private String screen;
        private String viewport;
        private Long visibleMs;
        private String element;
        private String label;
        private String area;
        private String action;
        private String value;
        private String origin;
        private Integer status;
        private String code;

        private Builder(UsageEventType type, Instant occurredAt) {
            this.type = type;
            this.occurredAt = occurredAt;
        }

        public Builder sessionId(String v) { this.sessionId = v; return this; }
        public Builder viewId(String v) { this.viewId = v; return this; }
        public Builder screen(String v) { this.screen = v; return this; }
        public Builder viewport(String v) { this.viewport = v; return this; }
        public Builder visibleMs(Long v) { this.visibleMs = v; return this; }
        public Builder element(String v) { this.element = v; return this; }
        public Builder label(String v) { this.label = v; return this; }
        public Builder area(String v) { this.area = v; return this; }
        public Builder action(String v) { this.action = v; return this; }
        public Builder value(String v) { this.value = v; return this; }
        public Builder origin(String v) { this.origin = v; return this; }
        public Builder status(Integer v) { this.status = v; return this; }
        public Builder code(String v) { this.code = v; return this; }

        public UsageEventDraft build() {
            return new UsageEventDraft(type, occurredAt, sessionId, viewId, screen, viewport, visibleMs,
                    element, label, area, action, value, origin, status, code);
        }
    }
}
