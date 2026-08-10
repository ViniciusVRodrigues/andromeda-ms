package andromeda.sei.ms.feature.azure.infrastructure.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AdoWorkItemTypeStateDto(
        @JsonProperty("name") String name,
        @JsonProperty("color") String color,
        @JsonProperty("category") String category
) {}
