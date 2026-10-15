package andromeda.sei.ms.feature.usage.presentation.dto;

import andromeda.sei.ms.feature.usage.domain.FunnelEventDraft;
import andromeda.sei.ms.feature.usage.domain.FunnelRules;
import andromeda.sei.ms.feature.usage.domain.FunnelStage;
import andromeda.sei.ms.feature.usage.domain.UsageEventRules;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * One anonymous funnel event. Everything here is 400 (the contract has no 422 for the public endpoint).
 * {@code COMPLETE} is not in the stage pattern. Unknown JSON fields are dropped.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FunnelEventRequest(
        @NotNull @Pattern(regexp = FunnelRules.CLIENT_STAGE_REGEX) String stage,
        @NotNull Instant occurredAt,
        @Size(max = 32) @Pattern(regexp = FunnelRules.STEP_REGEX) String step,
        @Min(0) @Max(FunnelRules.MAX_VISIBLE_MS) Long visibleMs,
        @Pattern(regexp = FunnelRules.VALUE_REGEX) String value,
        @Pattern(regexp = UsageEventRules.VIEWPORT_REGEX) String viewport) {

    public FunnelEventDraft toDraft() {
        return new FunnelEventDraft(FunnelStage.valueOf(stage), occurredAt, step, visibleMs, value, viewport);
    }
}
