package andromeda.sei.ms.feature.usage.presentation.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UsageEventsRequest(
        @NotNull @Size(min = 1, max = 50) List<@NotNull @Valid UsageEventRequest> events) {
}
