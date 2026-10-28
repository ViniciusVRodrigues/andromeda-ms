package andromeda.sei.ms.feature.azure.infrastructure.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Azure DevOps {@code GitRef}: {@code name} is the full ref ({@code refs/heads/main}). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AzRefDto(String name, String objectId) {}
