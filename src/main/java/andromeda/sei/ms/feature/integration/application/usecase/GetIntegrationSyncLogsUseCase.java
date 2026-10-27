package andromeda.sei.ms.feature.integration.application.usecase;

import andromeda.sei.ms.feature.integration.application.dto.SyncLogOutput;
import andromeda.sei.ms.feature.integration.domain.Integration;
import andromeda.sei.ms.feature.integration.domain.IntegrationSyncLog;
import andromeda.sei.ms.feature.integration.domain.IntegrationSyncLogRepository;
import andromeda.sei.ms.shared.application.security.EffectivePermissionService;
import andromeda.sei.ms.shared.domain.security.UserPermissionSnapshot;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class GetIntegrationSyncLogsUseCase {

    private static final int MAX_RESULTS = 100;

    private final IntegrationSyncLogRepository syncLogRepository;
    private final EffectivePermissionService effectivePermissionService;

    /**
     * Logs of the integration, filtered by what the caller can read (project-scoped-permissions
     * 7.3): a project's logs only when the caller holds the integration's read code on it, and
     * organization-level logs (no project) only when they hold it at organization level. The filter
     * runs after the {@value #MAX_RESULTS}-entry fetch, so a caller with few projects may see fewer
     * than that many.
     */
    public List<SyncLogOutput> execute(Integration integration, String projectId, UserPermissionSnapshot caller) {
        String orgId = TenantContextHolder.get();
        String code = integration.readPermission();
        Set<String> visibleProjectIds = effectivePermissionService.projectIdsWith(caller, code);
        boolean orgLevel = caller.resolver().hasOrg(code);
        List<IntegrationSyncLog> logs = syncLogRepository
                .findByOrganizationIdAndIntegration(orgId, integration, projectId, MAX_RESULTS).stream()
                .filter(l -> l.getProjectId() == null ? orgLevel : visibleProjectIds.contains(l.getProjectId()))
                .toList();

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
                l.getErrorMessage(),
                l.getHttpStatus(),
                l.getErrorDetail(),
                l.getItemsUnmappedType(),
                l.getUnmappedTypes()
        )).toList();
    }
}
