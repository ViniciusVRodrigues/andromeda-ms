package andromeda.sei.ms.feature.usage.domain;

import java.time.Instant;

/**
 * One validated usage event, ready to store. Built only through {@link #fromClient} (validates, keeps
 * just the fields of the type, re-sanitizes the label, fixes the clock) or {@link #login} (server).
 */
public final class UsageEvent {

    private final String organizationId;
    private final String userId;
    private final UsageEventType type;
    private final String screen;
    private final String viewId;
    private final String sessionId;
    private final Long visibleMs;
    private final String element;
    private final String label;
    private final String area;
    private final String action;
    private final String value;
    private final String viewport;
    private final String origin;
    private final Integer status;
    private final String code;
    private final Instant occurredAt;
    private final Instant createdAt;

    private UsageEvent(Builder b) {
        this.organizationId = b.organizationId;
        this.userId = b.userId;
        this.type = b.type;
        this.screen = b.screen;
        this.viewId = b.viewId;
        this.sessionId = b.sessionId;
        this.visibleMs = b.visibleMs;
        this.element = b.element;
        this.label = b.label;
        this.area = b.area;
        this.action = b.action;
        this.value = b.value;
        this.viewport = b.viewport;
        this.origin = b.origin;
        this.status = b.status;
        this.code = b.code;
        this.occurredAt = b.occurredAt;
        this.createdAt = b.createdAt;
    }

    public static UsageEvent fromClient(String organizationId, String userId, UsageEventDraft d, Instant receivedAt) {
        if (d == null || d.type() == null) throw invalid("tipo ausente");
        if (d.type() == UsageEventType.LOGIN) throw invalid("LOGIN é gravado só pelo servidor");

        require(d.screen() != null && d.screen().length() <= UsageEventRules.SCREEN_MAX_LENGTH
                && UsageEventRules.SCREEN.matcher(d.screen()).matches(), "tela fora do formato");
        require(d.sessionId() != null && UsageEventRules.UUID_V4.matcher(d.sessionId()).matches(), "sessionId inválido");
        require(d.viewId() != null && UsageEventRules.UUID_V4.matcher(d.viewId()).matches(), "viewId inválido");
        require(d.viewport() == null || UsageEventRules.VIEWPORTS.contains(d.viewport()), "viewport inválido");

        Builder b = new Builder();
        b.organizationId = organizationId;
        b.userId = userId;
        b.type = d.type();
        b.screen = d.screen();
        b.viewId = d.viewId();
        b.sessionId = d.sessionId();
        b.viewport = d.viewport();
        b.occurredAt = withinWindow(d.occurredAt(), receivedAt);
        b.createdAt = receivedAt;

        switch (d.type()) {
            case PAGE_VIEW -> { }
            case SCREEN_TIME -> {
                require(d.visibleMs() != null, "SCREEN_TIME sem visibleMs");
                require(d.visibleMs() >= 0 && d.visibleMs() <= UsageEventRules.MAX_VISIBLE_MS, "visibleMs fora do intervalo");
                b.visibleMs = d.visibleMs();
            }
            case CLICK -> {
                require(d.element() != null && UsageEventRules.ELEMENTS.contains(d.element()), "CLICK sem element válido");
                require(d.area() != null && UsageEventRules.AREA.matcher(d.area()).matches(), "CLICK sem area válida");
                b.element = d.element();
                b.area = d.area();
                b.label = UsageLabelSanitizer.sanitize(d.label());
            }
            case ACTION -> {
                require(d.action() != null && UsageEventRules.ACTION.matcher(d.action()).matches(), "ACTION sem action válida");
                require(d.value() == null || UsageEventRules.VALUE.matcher(d.value()).matches(), "value fora do formato");
                b.action = d.action();
                b.value = d.value();
            }
            case ERROR -> {
                require(d.origin() != null && UsageEventRules.ORIGINS.contains(d.origin()), "ERROR sem origin válida");
                if ("api".equals(d.origin())) {
                    require(d.status() != null, "ERROR api sem status");
                    require(d.status() >= 0 && d.status() <= UsageEventRules.MAX_STATUS, "status fora do intervalo");
                    require(d.status() != UsageEventRules.UNAUTHORIZED, "401 não é erro: é sessão expirada");
                    b.status = d.status();
                } else {
                    require(d.status() == null, "status só vale em origin api");
                }
                require(d.code() != null && UsageEventRules.CODE.matcher(d.code()).matches(), "ERROR sem code válido");
                b.origin = d.origin();
                b.code = d.code();
            }
            default -> throw invalid("tipo não aceito");
        }
        return new UsageEvent(b);
    }

    /** Server-side login record; {@code value} is the flow that issued the token. */
    public static UsageEvent login(String organizationId, String userId, LoginMethod method, Instant at) {
        Builder b = new Builder();
        b.organizationId = organizationId;
        b.userId = userId;
        b.type = UsageEventType.LOGIN;
        b.value = method.value();
        b.occurredAt = at;
        b.createdAt = at;
        return new UsageEvent(b);
    }

    /** Rebuilds a stored event (read side of the export). */
    public static UsageEvent restore(String organizationId, String userId, UsageEventType type, String screen,
                                     String viewId, String sessionId, Long visibleMs, String element, String label,
                                     String area, String action, String value, String viewport, String origin,
                                     Integer status, String code, Instant occurredAt, Instant createdAt) {
        Builder b = new Builder();
        b.organizationId = organizationId;
        b.userId = userId;
        b.type = type;
        b.screen = screen;
        b.viewId = viewId;
        b.sessionId = sessionId;
        b.visibleMs = visibleMs;
        b.element = element;
        b.label = label;
        b.area = area;
        b.action = action;
        b.value = value;
        b.viewport = viewport;
        b.origin = origin;
        b.status = status;
        b.code = code;
        b.occurredAt = occurredAt;
        b.createdAt = createdAt;
        return new UsageEvent(b);
    }

    private static Instant withinWindow(Instant occurredAt, Instant receivedAt) {
        if (occurredAt == null
                || occurredAt.isBefore(receivedAt.minus(UsageEventRules.CLOCK_PAST_TOLERANCE))
                || occurredAt.isAfter(receivedAt.plus(UsageEventRules.CLOCK_FUTURE_TOLERANCE))) {
            return receivedAt;
        }
        return occurredAt;
    }

    private static void require(boolean condition, String reason) {
        if (!condition) throw invalid(reason);
    }

    private static UsageEventInvalidException invalid(String reason) {
        return new UsageEventInvalidException(reason);
    }

    private static final class Builder {
        String organizationId;
        String userId;
        UsageEventType type;
        String screen;
        String viewId;
        String sessionId;
        Long visibleMs;
        String element;
        String label;
        String area;
        String action;
        String value;
        String viewport;
        String origin;
        Integer status;
        String code;
        Instant occurredAt;
        Instant createdAt;
    }

    public String getOrganizationId() { return organizationId; }
    public String getUserId() { return userId; }
    public UsageEventType getType() { return type; }
    public String getScreen() { return screen; }
    public String getViewId() { return viewId; }
    public String getSessionId() { return sessionId; }
    public Long getVisibleMs() { return visibleMs; }
    public String getElement() { return element; }
    public String getLabel() { return label; }
    public String getArea() { return area; }
    public String getAction() { return action; }
    public String getValue() { return value; }
    public String getViewport() { return viewport; }
    public String getOrigin() { return origin; }
    public Integer getStatus() { return status; }
    public String getCode() { return code; }
    public Instant getOccurredAt() { return occurredAt; }
    public Instant getCreatedAt() { return createdAt; }
}
