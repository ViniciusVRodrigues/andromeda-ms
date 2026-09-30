package andromeda.sei.ms.feature.azure.infrastructure.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Azure DevOps {@code GitRepository}. {@code defaultBranch} is a full ref ({@code refs/heads/main})
 * and is absent on an empty repository.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AzRepositoryDto(String id, String name, String defaultBranch,
                              @JsonProperty("isDisabled") Boolean disabled, AzProjectRefDto project) {

    public boolean isDisabled() {
        return Boolean.TRUE.equals(disabled);
    }
}
