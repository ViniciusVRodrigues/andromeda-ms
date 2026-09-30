package andromeda.sei.ms.feature.project.application.dto;

import andromeda.sei.ms.feature.integration.domain.Integration;
import andromeda.sei.ms.feature.integration.domain.IntegrationSyncLog;
import andromeda.sei.ms.feature.integration.domain.SyncStatus;
import andromeda.sei.ms.feature.integration.domain.SyncTrigger;

import java.time.Instant;
import java.util.List;

/**
 * Progresso de um refresh de projeto.
 *
 * @param status   {@code RUNNING}, {@code SUCCESS}, {@code PARTIAL} ou {@code FAILED}
 * @param bindings um item por binding, com o proprio status e codigo de erro
 */
public record ProjectRefreshStatusOutput(String syncId, String projectId, SyncStatus status, SyncTrigger triggeredBy,
                                         Instant startedAt, Instant finishedAt, List<BindingStatus> bindings) {

    public record BindingStatus(String bindingId, Integration integration, SyncStatus status, String errorCode,
                                String errorMessage, Instant startedAt, Instant finishedAt,
                                Integer itemsProcessed, Integer itemsFailed) {

        public static BindingStatus from(IntegrationSyncLog child) {
            return new BindingStatus(child.getBindingId(), child.getIntegration(), child.getStatus(),
                    child.getErrorCode(), child.getErrorMessage(), child.getStartedAt(), child.getFinishedAt(),
                    child.getItemsProcessed(), child.getItemsFailed());
        }
    }

    public static ProjectRefreshStatusOutput from(IntegrationSyncLog parent, List<IntegrationSyncLog> children) {
        return new ProjectRefreshStatusOutput(parent.getId(), parent.getProjectId(), parent.getStatus(),
                parent.getTriggeredBy(), parent.getStartedAt(), parent.getFinishedAt(),
                children.stream().map(BindingStatus::from).toList());
    }
}
