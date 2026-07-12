package andromeda.sei.ms.feature.azure.infrastructure.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AdoWorkItemTypeDto(String name, String referenceName) {}
