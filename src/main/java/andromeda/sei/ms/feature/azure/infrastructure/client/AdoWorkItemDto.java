package andromeda.sei.ms.feature.azure.infrastructure.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

/**
 * {@code relations} is only populated because {@link AzureDevOpsClient#getWorkItemsBatch}
 * requests {@code $expand=all} -- Azure DevOps has no scalar "parent" field; the parent link
 * (if any) is expressed as a {@code System.LinkTypes.Hierarchy-Reverse} relation whose {@code url}
 * ends in the parent's work item id.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AdoWorkItemDto(int id, Map<String, Object> fields, List<AdoWorkItemRelationDto> relations) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AdoWorkItemRelationDto(String rel, String url) {}
}
