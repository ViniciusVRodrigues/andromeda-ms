package andromeda.sei.ms.feature.azure.application.dto;

import java.util.List;

/** An identity of the persisted org directory; {@code linkedUserId} is the Andromeda user already linked to it. */
public record AzureUserOutput(String azureId, String displayName, String azureEmail,
                              List<String> sources, String linkedUserId) {}
