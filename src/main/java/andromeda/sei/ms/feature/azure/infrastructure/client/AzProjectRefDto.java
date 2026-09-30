package andromeda.sei.ms.feature.azure.infrastructure.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Azure DevOps {@code TeamProjectReference} embedded in a repository. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AzProjectRefDto(String id, String name) {}
