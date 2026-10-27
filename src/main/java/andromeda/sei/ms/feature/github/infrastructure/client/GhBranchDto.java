package andromeda.sei.ms.feature.github.infrastructure.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** One entry of {@code GET /repos/{o}/{r}/branches}: only the short name is read. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GhBranchDto(@JsonProperty("name") String name) {}
