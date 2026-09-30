package andromeda.sei.ms.feature.azure.application.usecase;

import andromeda.sei.ms.feature.azure.domain.AzureConnectionRepository;
import andromeda.sei.ms.feature.azure.domain.AzureProjectBinding;
import andromeda.sei.ms.feature.azure.domain.AzureProjectBindingRepository;
import andromeda.sei.ms.feature.azure.domain.exception.AzureBindingNotFoundException;
import andromeda.sei.ms.feature.azure.domain.exception.AzureConnectionNotFoundException;
import andromeda.sei.ms.feature.integration.domain.Integration;
import andromeda.sei.ms.feature.integration.domain.SyncOperation;
import andromeda.sei.ms.feature.integration.domain.SyncTrigger;
import andromeda.sei.ms.feature.sync.application.SyncOrchestrator;
import andromeda.sei.ms.feature.sync.application.SyncRequest;
import andromeda.sei.ms.feature.sync.application.SyncTargetResolver;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Sync manual do Azure DevOps, assincrono (on-demand-refresh): valida a conexao, submete os
 * bindings ao {@link SyncOrchestrator} e devolve o {@code syncId} antes de qualquer chamada
 * externa. O orquestrador aplica o cooldown por binding, recalcula as metricas e atualiza a saude
 * da conexao — um sync bem-sucedido tira a conexao de {@code ERROR}.
 */
@Component
@RequiredArgsConstructor
public class TriggerAzureSyncUseCase {

    private final AzureConnectionRepository azureConnectionRepository;
    private final AzureProjectBindingRepository azureProjectBindingRepository;
    private final SyncTargetResolver syncTargetResolver;
    private final SyncOrchestrator syncOrchestrator;

    /**
     * Sincroniza todos os bindings {@code syncEnabled && active} da organizacao.
     *
     * @return {@code syncId} do sync (log pai {@code ORGANIZATION_SYNC})
     * @throws AzureConnectionNotFoundException sem conexao ativa
     */
    public String execute() {
        String orgId = requireConnection();
        return syncOrchestrator.submit(SyncRequest.organization(orgId, Integration.AZURE_DEVOPS,
                syncTargetResolver.forOrganization(orgId, Integration.AZURE_DEVOPS, null), SyncTrigger.MANUAL));
    }

    /**
     * Sincroniza um unico binding, independente do {@code syncEnabled} — quem chama (conclusao do
     * wizard, edicao do mapeamento) ja decidiu que ele deve sincronizar.
     *
     * @return {@code syncId} do sync
     */
    public String execute(String bindingId) {
        String orgId = requireConnection();
        AzureProjectBinding binding = azureProjectBindingRepository
                .findByIdAndOrganizationIdAndActiveTrue(bindingId, orgId)
                .orElseThrow(() -> new AzureBindingNotFoundException(bindingId));
        return syncOrchestrator.submit(new SyncRequest(orgId, SyncOperation.ORGANIZATION_SYNC,
                Integration.AZURE_DEVOPS, binding.getProjectId(),
                List.of(SyncTargetResolver.toTarget(binding)), SyncTrigger.MANUAL, false));
    }

    private String requireConnection() {
        String orgId = TenantContextHolder.get();
        azureConnectionRepository.findByOrganizationIdAndActiveTrue(orgId)
                .orElseThrow(() -> new AzureConnectionNotFoundException(orgId));
        return orgId;
    }
}
