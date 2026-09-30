package andromeda.sei.ms.feature.azure.infrastructure.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Azure DevOps {@code GitCommitRef}; the Git author carries only name, email and date. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AzCommitDto(String commitId, GitUser author, String comment) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GitUser(String name, String email, String date) {}
}
