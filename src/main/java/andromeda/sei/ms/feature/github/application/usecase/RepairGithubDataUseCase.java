package andromeda.sei.ms.feature.github.application.usecase;

import andromeda.sei.ms.feature.integration.domain.Integration;
import andromeda.sei.ms.feature.integration.domain.SyncTrigger;
import andromeda.sei.ms.feature.sync.application.SyncOrchestrator;
import andromeda.sei.ms.feature.sync.application.SyncRequest;
import andromeda.sei.ms.feature.sync.application.SyncTargetResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Reconstroi os dados do GitHub de uma organizacao sem apaga-los: zera o watermark de leitura
 * dos bindings e deixa o sync reler tudo o que cabe na janela de backfill.
 *
 * <p>No GitHub o watermark e o proprio {@code lastSyncAt} do binding, e isso aqui e seguro: nenhum
 * outro sync escreve nesse campo — {@code SyncGithubRepoDataUseCase} o captura numa variavel local
 * antes de qualquer sub-sync rodar.
 *
 * <p>Passa pelo {@link SyncOrchestrator} (on-demand-refresh): o watermark e zerado no inicio do sync
 * de cada binding, em background, e o primeiro sync e limitado por {@code backfillDays}.
 *
 * <p>Preserva os registros existentes: commits, PRs e reviews sao upsert por id externo.
 */
@Service
@RequiredArgsConstructor
public class RepairGithubDataUseCase {

    private final SyncTargetResolver syncTargetResolver;
    private final SyncOrchestrator syncOrchestrator;

    /**
     * @param projectId quando null, repara todos os bindings sincronizaveis da organizacao
     * @return {@code syncId} do sync de reparo
     */
    public String execute(String organizationId, String projectId) {
        return syncOrchestrator.submit(SyncRequest.organization(organizationId, Integration.GITHUB,
                syncTargetResolver.forOrganization(organizationId, Integration.GITHUB, projectId),
                SyncTrigger.MANUAL).withResetWatermarks());
    }
}
