package andromeda.sei.ms.feature.azure.application.usecase;

import andromeda.sei.ms.feature.azure.domain.exception.AzureBindingNotFoundException;
import andromeda.sei.ms.feature.integration.domain.Integration;
import andromeda.sei.ms.feature.integration.domain.SyncTrigger;
import andromeda.sei.ms.feature.sync.application.SyncOrchestrator;
import andromeda.sei.ms.feature.sync.application.SyncRequest;
import andromeda.sei.ms.feature.sync.application.SyncTargetResolver;
import andromeda.sei.ms.feature.sync.domain.SyncTarget;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Reconstroi os dados do Azure DevOps de uma organizacao sem apaga-los: zera o watermark e deixa
 * o sync rodar como primeiro sync.
 *
 * <p>E o caminho de recuperacao para quem ficou com historico incompleto por causa do defeito do
 * watermark compartilhado — um sync incremental nunca volta atras para buscar o que pulou.
 *
 * <p>Preserva os registros existentes: o sync faz upsert por {@code externalId}, entao ids locais,
 * overrides manuais e metricas derivadas sobrevivem; o que faltava e criado.
 *
 * <p>Passa pelo {@link SyncOrchestrator} (on-demand-refresh): o watermark e zerado no inicio do
 * sync de cada binding, em background. Como o primeiro sync e limitado pela janela de backfill da
 * organizacao, o repair rele {@code backfillDays} dias; para ir mais longe, aumente a janela
 * (ate 730 dias) antes de reparar.
 *
 * <p>Diferente de {@link ForceResyncProjectUseCase}, que percorre os work items <b>ja gravados</b>
 * para reconstruir historico de transicoes e por construcao nao alcanca item que nunca foi baixado.
 */
@Service
@RequiredArgsConstructor
public class RepairAzureDataUseCase {

    private final SyncTargetResolver syncTargetResolver;
    private final SyncOrchestrator syncOrchestrator;

    /**
     * @param projectId quando null, repara todos os bindings sincronizaveis da organizacao
     * @return {@code syncId} do sync de reparo
     */
    public String execute(String organizationId, String projectId) {
        List<SyncTarget> targets = syncTargetResolver.forOrganization(organizationId, Integration.AZURE_DEVOPS, projectId);
        if (targets.isEmpty()) {
            throw new AzureBindingNotFoundException(projectId == null ? organizationId : projectId);
        }
        return syncOrchestrator.submit(SyncRequest.organization(organizationId, Integration.AZURE_DEVOPS, targets,
                SyncTrigger.MANUAL).withResetWatermarks());
    }
}
