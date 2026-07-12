package andromeda.sei.ms.feature.azure.infrastructure.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AdoWorkItemTypeFieldDto(
        @JsonProperty("referenceName") String referenceName,
        @JsonProperty("name") String name,
        @JsonProperty("type") String type,
        @JsonProperty("required") boolean required
) {}
