package andromeda.sei.ms.feature.github.infrastructure.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Response of {@code GET /user}, used to validate a PAT on connection configuration. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GhUserDto(
        @JsonProperty("id") long id,
        @JsonProperty("login") String login) {
}
