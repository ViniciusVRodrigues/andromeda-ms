package andromeda.sei.ms.feature.azure.application.usecase;

import andromeda.sei.ms.feature.azure.domain.AzureProjectBinding;
import andromeda.sei.ms.feature.azure.domain.AzureProjectBindingRepository;
import andromeda.sei.ms.feature.azure.domain.exception.AzureBindingNotFoundException;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Reconstroi os dados do Azure DevOps de uma organizacao sem apaga-los: zera o watermark e deixa
 * o proximo sync rodar em FULL.
 *
 * <p>E o caminho de recuperacao para quem ficou com historico incompleto por causa do defeito do
 * watermark compartilhado — um sync incremental nunca volta atras para buscar o que pulou.
 *
 * <p>Preserva os registros existentes: o sync faz upsert por {@code externalId}, entao ids locais,
 * overrides manuais e metricas derivadas sobrevivem; o que faltava e criado.
 *
 * <p>Diferente de {@link ForceResyncProjectUseCase}, que percorre os work items <b>ja gravados</b>
 * para reconstruir historico de transicoes e por construcao nao alcanca item que nunca foi baixado.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RepairAzureDataUseCase {

    private final AzureProjectBindingRepository azureProjectBindingRepository;
    private final TriggerAzureSyncUseCase triggerAzureSyncUseCase;

    /**
     * @param projectId quando null, repara todos os bindings sincronizaveis da organizacao
     */
    @Async
    public void execute(String organizationId, String projectId) {
        /* TenantContextHolder e um ThreadLocal simples: nao atravessa a fronteira do @Async.
         * Sem reposicionar aqui, TriggerAzureSyncUseCase leria tenant null nesta thread. O
         * finally e obrigatorio — threads do pool sao reutilizadas, e um tenant vazado daria
         * acesso cruzado entre organizacoes. */
        TenantContextHolder.set(organizationId);
        try {
            List<AzureProjectBinding> bindings = azureProjectBindingRepository
                    .findAllByOrganizationIdAndSyncEnabledTrueAndActiveTrue(organizationId);

            List<AzureProjectBinding> alvos = projectId == null
                    ? bindings
                    : bindings.stream().filter(b -> projectId.equals(b.getProjectId())).toList();

            if (alvos.isEmpty()) {
                throw new AzureBindingNotFoundException(projectId == null ? organizationId : projectId);
            }

            for (AzureProjectBinding b : alvos) {
                b.setWorkItemsWatermark(null);
                azureProjectBindingRepository.save(b);
                log.info("Repair: watermark zerado para org={} projeto={}", organizationId, b.getProjectId());
            }

            /* O sync em si ja registra em integration_sync_logs e ja e idempotente por upsert. */
            for (AzureProjectBinding b : alvos) {
                triggerAzureSyncUseCase.execute(b.getId());
            }
        } finally {
            TenantContextHolder.clear();
        }
    }
}
