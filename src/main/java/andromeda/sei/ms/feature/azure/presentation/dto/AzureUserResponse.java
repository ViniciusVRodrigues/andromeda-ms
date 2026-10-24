package andromeda.sei.ms.feature.azure.presentation.dto;

import java.util.List;

public record AzureUserResponse(String azureId, String displayName, String azureEmail,
                                List<String> sources, String linkedUserId) {}
