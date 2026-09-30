package andromeda.sei.ms.feature.azure.infrastructure.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Azure DevOps {@code ResourceRef} of a work item linked to a pull request. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AzWorkItemRefDto(String id, String url) {}
