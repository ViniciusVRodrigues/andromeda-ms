package andromeda.sei.ms.feature.usage.presentation.dto;

import andromeda.sei.ms.feature.usage.domain.UsageEventDraft;
import andromeda.sei.ms.feature.usage.domain.UsageEventRules;
import andromeda.sei.ms.feature.usage.domain.UsageEventType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * One event as the client sends it. Format problems are 400 here (Bean Validation); fields that do not
 * fit the type are 422 in the domain. Unknown JSON fields (message, stack, userId, organizationId...)
 * are dropped: this record simply has no place to keep them.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UsageEventRequest(
        @NotNull @Pattern(regexp = UsageEventRules.CLIENT_TYPE_REGEX) String type,
        @NotNull Instant occurredAt,
        @NotNull @Pattern(regexp = UsageEventRules.UUID_V4_REGEX) String sessionId,
        @NotNull @Pattern(regexp = UsageEventRules.UUID_V4_REGEX) String viewId,
        @NotNull @Size(max = UsageEventRules.SCREEN_MAX_LENGTH) @Pattern(regexp = UsageEventRules.SCREEN_REGEX) String screen,
        @Pattern(regexp = UsageEventRules.VIEWPORT_REGEX) String viewport,
        @Min(0) @Max(UsageEventRules.MAX_VISIBLE_MS) Long visibleMs,
        @Pattern(regexp = UsageEventRules.ELEMENT_REGEX) String element,
        /* the server cuts it at 60; the bound here only keeps an abusive payload out */
        @Size(max = 500) String label,
        @Pattern(regexp = UsageEventRules.AREA_REGEX) String area,
        @Pattern(regexp = UsageEventRules.ACTION_REGEX) String action,
        @Pattern(regexp = UsageEventRules.VALUE_REGEX) String value,
        @Pattern(regexp = UsageEventRules.ORIGIN_REGEX) String origin,
        @Min(0) @Max(UsageEventRules.MAX_STATUS) Integer status,
        @Pattern(regexp = UsageEventRules.CODE_REGEX) String code) {

    public UsageEventDraft toDraft() {
        return new UsageEventDraft(UsageEventType.valueOf(type), occurredAt, sessionId, viewId, screen, viewport,
                visibleMs, element, label, area, action, value, origin, status, code);
    }
}
