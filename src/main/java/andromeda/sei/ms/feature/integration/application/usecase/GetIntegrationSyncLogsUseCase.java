package andromeda.sei.ms.feature.integration.application.usecase;

import andromeda.sei.ms.feature.integration.application.dto.SyncLogOutput;
import andromeda.sei.ms.feature.integration.domain.Integration;
import andromeda.sei.ms.feature.integration.domain.IntegrationSyncLog;
import andromeda.sei.ms.feature.integration.domain.IntegrationSyncLogRepository;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class GetIntegrationSyncLogsUseCase {

    private static final int MAX_RESULTS = 100;

    private final IntegrationSyncLogRepository syncLogRepository;

    public List<SyncLogOutput> execute(Integration integration, String projectId) {
        String orgId = TenantContextHolder.get();
        List<IntegrationSyncLog> logs = syncLogRepository
                .findByOrganizationIdAndIntegration(orgId, integration, projectId, MAX_RESULTS);

        return logs.stream().map(l -> new SyncLogOutput(
                l.getId(),
                l.getIntegration(),
                l.getOperation(),
                l.getStatus(),
                l.getTriggeredBy(),
                l.getProjectId(),
                l.getStartedAt(),
                l.getFinishedAt(),
                l.getDurationMs(),
                l.getItemsProcessed(),
                l.getItemsFailed(),
                l.getErrorCode(),
                l.getErrorMessage()
        )).toList();
    }
}
