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
     * {@code User.githubId}, which stores the numeric id (BUG 2 fix, design.md D2). {@code type}
     * is {@code User}, {@code Bot} or {@code Organization}.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UserDto(@JsonProperty("login") String login, @JsonProperty("id") Long id,
                          @JsonProperty("type") String type) {

        private static final String BOT_TYPE = "Bot";
        private static final String BOT_LOGIN_SUFFIX = "[bot]";

        public UserDto(String login, Long id) {
            this(login, id, null);
        }

        /** review-metrics: a GitHub actor is a bot when its type is Bot or its login ends with [bot]. */
        public boolean isBot() {
            return BOT_TYPE.equalsIgnoreCase(type) || (login != null && login.endsWith(BOT_LOGIN_SUFFIX));
        }
    }
}
