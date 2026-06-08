package andromeda.sei.ms.feature.integration.infrastructure.persistence;

import andromeda.sei.ms.feature.integration.domain.IntegrationSyncLog;
import org.springframework.stereotype.Component;

@Component
public class IntegrationSyncLogDocumentMapper {

    public IntegrationSyncLog toDomain(IntegrationSyncLogDocument doc) {
        return new IntegrationSyncLog(
                doc.getId(),
                doc.getOrganizationId(),
                doc.getIntegration(),
                doc.getOperation(),
                doc.getStatus(),
                doc.getTriggeredBy(),
                doc.getProjectId(),
                doc.getStartedAt(),
                doc.getFinishedAt(),
                doc.getDurationMs(),
                doc.getItemsProcessed(),
                doc.getItemsFailed(),
                doc.getErrorCode(),
                doc.getErrorMessage()
        );
    }

    public IntegrationSyncLogDocument toDocument(IntegrationSyncLog log) {
        return IntegrationSyncLogDocument.builder()
                .id(log.getId())
                .organizationId(log.getOrganizationId())
                .integration(log.getIntegration())
                .operation(log.getOperation())
                .status(log.getStatus())
                .triggeredBy(log.getTriggeredBy())
                .projectId(log.getProjectId())
                .startedAt(log.getStartedAt())
                .finishedAt(log.getFinishedAt())
                .durationMs(log.getDurationMs())
                .itemsProcessed(log.getItemsProcessed())
                .itemsFailed(log.getItemsFailed())
                .errorCode(log.getErrorCode())
                .errorMessage(log.getErrorMessage())
                .build();
    }
}
