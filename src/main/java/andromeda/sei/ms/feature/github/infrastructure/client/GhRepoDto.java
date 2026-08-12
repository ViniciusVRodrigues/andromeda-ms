package andromeda.sei.ms.feature.github.infrastructure.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GhRepoDto(
        @JsonProperty("id") long id,
        @JsonProperty("name") String name,
        @JsonProperty("full_name") String fullName,
        @JsonProperty("owner") OwnerDto owner,
        @JsonProperty("updated_at") String updatedAt) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OwnerDto(@JsonProperty("login") String login) {}
}
