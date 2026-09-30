package andromeda.sei.ms.feature.github.infrastructure.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * {@code default_branch} ships in the same payload, so persisting it costs no extra request; the
 * delivery metrics only count merges into it (metrics-correctness D2).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GhRepoDto(
        @JsonProperty("id") long id,
        @JsonProperty("name") String name,
        @JsonProperty("full_name") String fullName,
        @JsonProperty("owner") OwnerDto owner,
        @JsonProperty("updated_at") String updatedAt,
        @JsonProperty("default_branch") String defaultBranch) {

    public GhRepoDto(long id, String name, String fullName, OwnerDto owner, String updatedAt) {
        this(id, name, fullName, owner, updatedAt, null);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OwnerDto(@JsonProperty("login") String login) {}
}
