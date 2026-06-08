package andromeda.sei.ms.feature.azure.presentation.dto;

import java.time.Instant;

public record AzureProjectBindingResponse(
        String id,
        String projectId,
        String azureProjectId,
        String azureProjectName,
        boolean syncEnabled,
        Instant lastSyncAt,
        WorkflowConfigResponse workflowConfig
) {}
