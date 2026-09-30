package andromeda.sei.ms.feature.azure.infrastructure.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Azure DevOps {@code GitPullRequest}, only the fields the sync uses. Dates stay ISO-8601 strings
 * (seven fraction digits in the service payloads) and are parsed by the mapper. There is no
 * "last updated" field: see {@code AzurePullRequestMapper}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AzPullRequestDto(
        Integer pullRequestId,
        String status,
        AzIdentityDto createdBy,
        String creationDate,
        String closedDate,
        String title,
        String description,
        String sourceRefName,
        String targetRefName,
        @JsonProperty("isDraft") Boolean draft,
        List<AzLabelDto> labels) {}
