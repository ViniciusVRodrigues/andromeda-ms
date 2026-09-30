package andromeda.sei.ms.feature.integration.infrastructure.persistence;

import andromeda.sei.ms.feature.integration.domain.IntegrationSyncLog;
import org.springframework.stereotype.Component;

@Component
public class IntegrationSyncLogDocumentMapper {

    public IntegrationSyncLog toDomain(IntegrationSyncLogDocument doc) {
        IntegrationSyncLog log = new IntegrationSyncLog(
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
        log.setHttpStatus(doc.getHttpStatus());
        log.setErrorDetail(doc.getErrorDetail());
        log.setParentSyncId(doc.getParentSyncId());
        log.setBindingId(doc.getBindingId());
        return log;
    }

    public IntegrationSyncLogDocument toDocument(IntegrationSyncLog log) {
        var builder = IntegrationSyncLogDocument.builder()
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
                .httpStatus(log.getHttpStatus())
                .errorDetail(log.getErrorDetail())
                .parentSyncId(log.getParentSyncId())
                .bindingId(log.getBindingId());

        if (log.getId() != null) {
            builder.id(log.getId());
        }

        return builder.build();
    }
}
