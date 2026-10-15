package andromeda.sei.ms.feature.usage.presentation.dto;

import andromeda.sei.ms.feature.usage.domain.FunnelEventDraft;
import andromeda.sei.ms.feature.usage.domain.FunnelFlow;
import andromeda.sei.ms.feature.usage.domain.FunnelRules;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FunnelEventsRequest(
        @NotNull @Pattern(regexp = FunnelRules.UUID_V4_REGEX) String funnelId,
        @NotNull @Pattern(regexp = FunnelRules.FLOW_REGEX) String flow,
        @NotNull @Size(min = 1, max = FunnelRules.MAX_BATCH) List<@NotNull @Valid FunnelEventRequest> events) {

    public FunnelFlow flowEnum() {
        return FunnelFlow.fromValue(flow).orElseThrow();
    }

    public List<FunnelEventDraft> drafts() {
        return events.stream().map(FunnelEventRequest::toDraft).toList();
    }

    /** Closed step list per flow and stage coherence; only checked once the field formats are valid. */
    @JsonIgnore
    @AssertTrue(message = "etapa incoerente com o fluxo")
    public boolean isEventsCoherentWithFlow() {
        if (flow == null || events == null) return true;
        FunnelFlow parsed = FunnelFlow.fromValue(flow).orElse(null);
        if (parsed == null) return true;
        for (FunnelEventRequest e : events) {
            if (e == null || e.stage() == null || !e.stage().matches(FunnelRules.CLIENT_STAGE_REGEX)) continue;
            if (FunnelRules.violation(parsed, e.toDraft()) != null) return false;
        }
        return true;
    }
}
