package andromeda.sei.ms.feature.connector.presentation.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.LocalDate;
import java.util.Map;

public record AiefUploadRequest(
        String datasetId,
        String gitHubLogin,
        @NotNull @JsonFormat(pattern = "yyyy-MM-dd") LocalDate day,
        @NotBlank String model,
        String workspaceId,
        String workspaceName,
        String machineId,
        String machineName,
        @NotNull @PositiveOrZero Integer inputTokens,
        @NotNull @PositiveOrZero Integer outputTokens,
        @NotNull @PositiveOrZero Integer interactions,
        String editor,
        Map<String, Object> fluencyMetrics
) {}
