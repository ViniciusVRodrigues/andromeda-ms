package andromeda.sei.ms.feature.project.application.usecase;

import andromeda.sei.ms.feature.integration.domain.IntegrationSyncLog;
import andromeda.sei.ms.feature.integration.domain.IntegrationSyncLogRepository;
import andromeda.sei.ms.feature.integration.domain.SyncOperation;
import andromeda.sei.ms.feature.project.application.dto.ProjectRefreshStatusOutput;
import andromeda.sei.ms.feature.project.domain.exception.ProjectRefreshNotFoundException;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Status de um refresh de projeto (log pai + filhos por binding), lido de
 * {@code integration_sync_logs}. Filtrado pela organizacao do chamador e pelo projeto do path:
 * um {@code syncId} de outra organizacao ou de outro projeto responde 404.
 */
@Component
@RequiredArgsConstructor
public class GetProjectRefreshStatusUseCase {

    private final IntegrationSyncLogRepository syncLogRepository;

    public ProjectRefreshStatusOutput execute(String projectId, String syncId) {
        String organizationId = TenantContextHolder.get();
        IntegrationSyncLog parent = syncLogRepository.findByIdAndOrganizationId(syncId, organizationId)
                .filter(log -> log.getOperation() == SyncOperation.PROJECT_REFRESH)
                .filter(log -> projectId.equals(log.getProjectId()))
                .orElseThrow(() -> new ProjectRefreshNotFoundException(syncId));

        List<IntegrationSyncLog> children =
                syncLogRepository.findAllByOrganizationIdAndParentSyncId(organizationId, parent.getId());
        return ProjectRefreshStatusOutput.from(parent, children);
    }
}
