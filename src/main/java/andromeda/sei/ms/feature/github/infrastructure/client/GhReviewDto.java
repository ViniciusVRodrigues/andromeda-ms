package andromeda.sei.ms.feature.github.infrastructure.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GhReviewDto(
        @JsonProperty("id") long id,
        @JsonProperty("user") UserDto user,
        @JsonProperty("state") String state,
        @JsonProperty("submitted_at") String submittedAt) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UserDto(@JsonProperty("login") String login) {}
}
