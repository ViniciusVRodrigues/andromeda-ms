package andromeda.sei.ms.feature.github.infrastructure.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GhPullRequestDto(
        @JsonProperty("id") long id,
        @JsonProperty("number") int number,
        @JsonProperty("title") String title,
        @JsonProperty("state") String state,
        @JsonProperty("user") UserDto user,
        @JsonProperty("created_at") String createdAt,
        @JsonProperty("updated_at") String updatedAt,
        @JsonProperty("merged_at") String mergedAt,
        @JsonProperty("closed_at") String closedAt) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UserDto(@JsonProperty("login") String login) {}
}
