package andromeda.sei.ms.feature.github.infrastructure.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * GitHub's commits endpoint has two independent author representations: the top-level
 * {@code author.login} (the GitHub account, may be null for commits authored outside GitHub)
 * and the nested {@code commit.author} block (raw git author name/email/date, always present).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GhCommitDto(
        @JsonProperty("sha") String sha,
        @JsonProperty("commit") CommitDetailDto commit,
        @JsonProperty("author") AuthorDto author) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CommitDetailDto(
            @JsonProperty("message") String message,
            @JsonProperty("author") GitAuthorDto author) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GitAuthorDto(
            @JsonProperty("name") String name,
            @JsonProperty("email") String email,
            @JsonProperty("date") String date) {}

    /**
     * {@code id} is GitHub's numeric user id (nullable — top-level {@code author} is null for
     * commits authored outside GitHub); used to resolve {@code authorUserId} against
     * {@code User.githubId}, which stores the numeric id (BUG 2 fix, design.md D2).
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AuthorDto(@JsonProperty("login") String login, @JsonProperty("id") Long id) {}
}
