package andromeda.sei.ms.feature.github.infrastructure.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GhReviewDto(
        @JsonProperty("id") long id,
        @JsonProperty("user") UserDto user,
        @JsonProperty("state") String state,
        @JsonProperty("submitted_at") String submittedAt) {

    /**
     * {@code id} is GitHub's numeric user id; used to resolve {@code reviewerUserId} against
     * {@code User.githubId}, which stores the numeric id (BUG 2 fix, design.md D2).
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UserDto(@JsonProperty("login") String login, @JsonProperty("id") Long id) {}
}
