package andromeda.sei.ms.feature.integration.application.usecase;

import andromeda.sei.ms.feature.azure.domain.AzureProjectBinding;
import andromeda.sei.ms.feature.azure.domain.AzureProjectBindingRepository;
import andromeda.sei.ms.feature.integration.domain.Integration;
import andromeda.sei.ms.feature.integration.domain.IntegrationDataWiper;
import andromeda.sei.ms.feature.integration.domain.IntegrationDataWiper.WipeScope;
import andromeda.sei.ms.feature.integration.domain.IntegrationSyncLog;
import andromeda.sei.ms.feature.integration.domain.IntegrationSyncLogRepository;
import andromeda.sei.ms.feature.integration.domain.SyncOperation;
import andromeda.sei.ms.feature.integration.domain.SyncStatus;
import andromeda.sei.ms.feature.integration.domain.SyncTrigger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Apaga permanentemente os dados de uma integracao numa organizacao.
 *
 * <p>Roda em background ({@code @Async}): o endpoint responde 202 e o acompanhamento e feito pelo
 * historico de {@code integration_sync_logs}, que a pagina de Integracoes ja exibe. Nao ha
 * colecao de jobs nem endpoint de status novos.
 *
 * <p>O registro de auditoria e gravado <b>depois</b> das delecoes, porque o proprio wipe limpa
 * {@code integration_sync_logs} da organizacao — uma entrada criada antes seria apagada por ele.
 *
 * <p>O wipe <b>nao</b> desconecta nem desvincula: limpar dados e tirar acesso sao acoes distintas,
 * e manter o vinculo permite "limpa e ressincroniza" sem reconfigurar PAT/OAuth.
 */
@Slf4j
@Service
public class WipeIntegrationDataUseCase {

    /** Several wipers may share an integration: Azure DevOps wipes Boards and Azure Repos data. */
    private final Map<Integration, List<IntegrationDataWiper>> wipers = new EnumMap<>(Integration.class);
    private final IntegrationSyncLogRepository syncLogRepository;
    private final AzureProjectBindingRepository azureProjectBindingRepository;

    public WipeIntegrationDataUseCase(List<IntegrationDataWiper> wiperList,
                                       IntegrationSyncLogRepository syncLogRepository,
                                       AzureProjectBindingRepository azureProjectBindingRepository) {
        for (IntegrationDataWiper w : wiperList) {
            this.wipers.computeIfAbsent(w.integration(), i -> new ArrayList<>()).add(w);
        }
        this.syncLogRepository = syncLogRepository;
        this.azureProjectBindingRepository = azureProjectBindingRepository;
    }

    /**
     * Dispara o wipe em background.
     *
     * @param organizationId tenant resolvido do contexto autenticado — nunca de parametro do cliente
     * @param scope          recorte ja decidido pelo chamador (o caso de IA resolve OWN/ALL a partir
     *                       das permissoes do token antes de chegar aqui)
     */
    @Async
    public void execute(String organizationId, Integration integration, WipeScope scope) {
        Instant startedAt = Instant.now();
        List<IntegrationDataWiper> integrationWipers = wipers.getOrDefault(integration, List.of());
        if (integrationWipers.isEmpty()) {
            log.error("Sem wiper registrado para {}", integration);
            return;
        }

        try {
            Map<String, Long> removidos = new LinkedHashMap<>();
            for (IntegrationDataWiper wiper : integrationWipers) {
                wiper.wipe(organizationId, scope).forEach((colecao, n) -> removidos.merge(colecao, n, Long::sum));
            }

            /* O historico da propria integracao tambem e dado dela. Apagado por ultimo, logo antes
             * de escrever o registro do wipe. */
            long logsRemovidos = syncLogRepository
                    .deleteByOrganizationIdAndIntegration(organizationId, integration);

            if (integration == Integration.AZURE_DEVOPS) {
                zerarWatermarks(organizationId, scope);
            }

            long total = removidos.values().stream().mapToLong(Long::longValue).sum() + logsRemovidos;
            registrar(organizationId, integration, scope, startedAt, SyncStatus.SUCCESS,
                    (int) total, 0, null, resumo(removidos, logsRemovidos));
            log.info("Wipe concluido: org={} integracao={} removidos={} logs={}",
                    organizationId, integration, removidos, logsRemovidos);

        } catch (Exception e) {
            log.error("Wipe falhou: org={} integracao={}", organizationId, integration, e);
            registrar(organizationId, integration, scope, startedAt, SyncStatus.FAILED,
                    0, 1, "INTEGRATION_WIPE_FAILED", e.getMessage());
        }
    }

    /**
     * Depois de apagar os work items, o watermark apontaria para uma janela cujos dados nao
     * existem mais: o proximo sync seria incremental e repopularia so o recente. Zerar forca FULL.
     */
    private void zerarWatermarks(String organizationId, WipeScope scope) {
        List<AzureProjectBinding> bindings = azureProjectBindingRepository
                .findAllByOrganizationIdAndSyncEnabledTrueAndActiveTrue(organizationId);
        for (AzureProjectBinding b : bindings) {
            if (scope.projectId() != null && !scope.projectId().equals(b.getProjectId())) continue;
            b.setWorkItemsWatermark(null);
            azureProjectBindingRepository.save(b);
        }
    }

    private String resumo(Map<String, Long> removidos, long logsRemovidos) {
        StringBuilder sb = new StringBuilder();
        removidos.forEach((colecao, n) -> sb.append(colecao).append('=').append(n).append(' '));
        sb.append("integration_sync_logs=").append(logsRemovidos);
        return sb.toString().trim();
    }

    private void registrar(String organizationId, Integration integration, WipeScope scope,
                            Instant startedAt, SyncStatus status, int processed, int failed,
                            String errorCode, String detalhe) {
        Instant finishedAt = Instant.now();
        syncLogRepository.save(new IntegrationSyncLog(
                null, organizationId, integration, SyncOperation.WIPE_DATA, status,
                SyncTrigger.MANUAL, scope.projectId(), startedAt, finishedAt,
                finishedAt.toEpochMilli() - startedAt.toEpochMilli(),
                processed, failed, errorCode, detalhe));
    }
}
