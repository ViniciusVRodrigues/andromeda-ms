package andromeda.sei.ms.feature.aiusage.application.dto;

import java.time.LocalDate;

public record AIUsageEntry(
        String datasetId,
        String gitHubLogin,
        LocalDate day,
        String model,
        String workspaceId,
        String workspaceName,
        String machineId,
        String machineName,
        long inputTokens,
        long outputTokens,
        int interactions,
        String editor
) {}
