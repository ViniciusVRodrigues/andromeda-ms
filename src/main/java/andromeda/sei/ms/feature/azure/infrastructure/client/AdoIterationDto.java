package andromeda.sei.ms.feature.azure.infrastructure.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AdoIterationDto(
        String id,
        String name,
        String path,
        Attributes attributes
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Attributes(
            @JsonProperty("startDate") String startDate,
            @JsonProperty("finishDate") String finishDate,
            @JsonProperty("timeFrame") String timeFrame
    ) {}
}
