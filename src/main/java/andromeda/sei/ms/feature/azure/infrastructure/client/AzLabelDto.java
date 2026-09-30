package andromeda.sei.ms.feature.azure.infrastructure.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Azure DevOps {@code WebApiTagDefinition} (pull request label). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AzLabelDto(String name, Boolean active) {}
