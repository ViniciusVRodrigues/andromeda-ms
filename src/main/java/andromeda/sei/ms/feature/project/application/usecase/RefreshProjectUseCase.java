package andromeda.sei.ms.feature.project.application.usecase;

import andromeda.sei.ms.feature.project.domain.ProjectRepository;
import andromeda.sei.ms.feature.project.domain.exception.ProjectNoIntegrationBindingException;
import andromeda.sei.ms.feature.project.domain.exception.ProjectNotFoundException;
import andromeda.sei.ms.feature.sync.application.SyncOrchestrator;
import andromeda.sei.ms.feature.sync.application.SyncRequest;
import andromeda.sei.ms.feature.sync.application.SyncTargetResolver;
import andromeda.sei.ms.feature.sync.domain.SyncTarget;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Refresh sob demanda de um projeto: sincroniza todos os bindings ativos e sincronizaveis do
 * projeto (work tracking e repositorios) e recalcula as metricas derivadas, em background.
 *
 * <p>Responde antes de qualquer chamada externa, com o {@code syncId} para acompanhar o progresso.
 */
@Component
@RequiredArgsConstructor
public class RefreshProjectUseCase {

    private final ProjectRepository projectRepository;
    private final SyncTargetResolver syncTargetResolver;
    private final SyncOrchestrator syncOrchestrator;

    /**
     * @return {@code syncId} do refresh
     * @throws ProjectNotFoundException              projeto inexistente, inativo ou de outra organizacao
     * @throws ProjectNoIntegrationBindingException   projeto sem binding sincronizavel
     * @throws andromeda.sei.ms.shared.domain.exception.TooManyRequestsException cooldown ou fila cheia
     */
    public String execute(String projectId) {
        String organizationId = TenantContextHolder.get();
        projectRepository.findActiveByIdAndOrganizationId(projectId, organizationId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));

        List<SyncTarget> targets = syncTargetResolver.forProject(organizationId, projectId);
        if (targets.isEmpty()) {
            throw new ProjectNoIntegrationBindingException(projectId);
        }
        return syncOrchestrator.submit(SyncRequest.projectRefresh(organizationId, projectId, targets));
    }
}
