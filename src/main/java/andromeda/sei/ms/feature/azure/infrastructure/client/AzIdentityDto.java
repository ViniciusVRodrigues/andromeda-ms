package andromeda.sei.ms.feature.azure.infrastructure.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Azure DevOps {@code IdentityRef} (PR author, comment author, reviewer). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AzIdentityDto(String id, String displayName, String uniqueName,
                            @JsonProperty("isContainer") Boolean container) {

    public boolean isContainer() {
        return Boolean.TRUE.equals(container);
    }
}
