package andromeda.sei.ms.feature.github.infrastructure.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

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
        @JsonProperty("closed_at") String closedAt,
        @JsonProperty("body") String body,
        @JsonProperty("head") HeadDto head,
        @JsonProperty("base") HeadDto base,
        @JsonProperty("draft") Boolean draft,
        @JsonProperty("labels") List<LabelDto> labels) {

    /** Pre-metrics-correctness shape, kept for callers that do not care about the new fields. */
    public GhPullRequestDto(long id, int number, String title, String state, UserDto user, String createdAt,
                            String updatedAt, String mergedAt, String closedAt, String body, HeadDto head) {
        this(id, number, title, state, user, createdAt, updatedAt, mergedAt, closedAt, body, head,
                null, null, null);
    }

    /**
     * {@code head.ref} is the source branch name and {@code base.ref} the target branch. Both,
     * like {@code body}, {@code draft} and {@code labels}, already ship in the pull request
     * payload, so reading them costs no additional request.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record HeadDto(@JsonProperty("ref") String ref) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LabelDto(@JsonProperty("name") String name) {}

    /**
     * {@code id} is GitHub's numeric user id; used to resolve {@code authorUserId} against
     * {@code User.githubId}, which stores the numeric id (BUG 2 fix, design.md D2).
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UserDto(@JsonProperty("login") String login, @JsonProperty("id") Long id) {}
}
