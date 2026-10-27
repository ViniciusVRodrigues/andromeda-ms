package andromeda.sei.ms.feature.integration.presentation.dto;

import andromeda.sei.ms.feature.integration.domain.Integration;
import andromeda.sei.ms.feature.integration.domain.SyncOperation;
import andromeda.sei.ms.feature.integration.domain.SyncStatus;
import andromeda.sei.ms.feature.integration.domain.SyncTrigger;

import java.time.Instant;

public record SyncLogResponse(
        String id,
        Integration integration,
        SyncOperation operation,
        SyncStatus status,
        SyncTrigger triggeredBy,
        String projectId,
        Instant startedAt,
        Instant finishedAt,
        Long durationMs,
        Integer itemsProcessed,
        Integer itemsFailed,
        String errorCode,
        String errorMessage,
        Integer httpStatus,
        String errorDetail,
        int itemsUnmappedType,
        java.util.List<String> unmappedTypes
) {}
