package andromeda.sei.ms.feature.github.infrastructure.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * GitHub's {@code /issues} endpoint returns both real issues and pull requests -- entries
 * with a non-null {@code pullRequest} key must be filtered out by the caller (D5/D7).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GhIssueDto(
        @JsonProperty("id") long id,
        @JsonProperty("number") int number,
        @JsonProperty("created_at") String createdAt,
        @JsonProperty("updated_at") String updatedAt,
        @JsonProperty("state_reason") String stateReason,
        @JsonProperty("pull_request") Object pullRequest) {

    public boolean isPullRequest() {
        return pullRequest != null;
    }
}
