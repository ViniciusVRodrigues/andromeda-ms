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

    /**
     * {@code id} is GitHub's numeric user id; used to resolve {@code authorUserId} against
     * {@code User.githubId}, which stores the numeric id (BUG 2 fix, design.md D2).
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UserDto(@JsonProperty("login") String login, @JsonProperty("id") Long id) {}
}
