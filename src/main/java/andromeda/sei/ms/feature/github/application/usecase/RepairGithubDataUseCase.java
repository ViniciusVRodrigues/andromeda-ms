package andromeda.sei.ms.feature.github.application.usecase;

import andromeda.sei.ms.feature.github.domain.GithubRepoBinding;
import andromeda.sei.ms.feature.github.domain.GithubRepoBindingRepository;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Reconstroi os dados do GitHub de uma organizacao sem apaga-los: zera o watermark de leitura
 * dos bindings e deixa o proximo sync reler tudo.
 *
 * <p>No GitHub o watermark e o proprio {@code lastSyncAt} do binding, e isso aqui e seguro: ao
 * contrario do Azure, nenhum outro sync escreve nesse campo — {@code SyncGithubRepoDataUseCase} o
 * captura numa variavel local antes de qualquer sub-sync rodar, que e justamente a disciplina que
 * o lado Azure tinha perdido.
 *
 * <p>Preserva os registros existentes: commits, PRs e reviews sao upsert por id externo.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RepairGithubDataUseCase {

    private final GithubRepoBindingRepository githubRepoBindingRepository;
    private final TriggerGithubSyncUseCase triggerGithubSyncUseCase;

    /**
     * @param projectId quando null, repara todos os bindings sincronizaveis da organizacao
     */
    @Async
    public void execute(String organizationId, String projectId) {
        /* ThreadLocal nao atravessa o @Async: sem reposicionar, TriggerGithubSyncUseCase leria
         * tenant null. O finally evita vazar tenant numa thread reutilizada do pool. */
        TenantContextHolder.set(organizationId);
        try {
            List<GithubRepoBinding> bindings = githubRepoBindingRepository
                    .findAllByOrganizationIdAndSyncEnabledTrueAndActiveTrue(organizationId);

            List<GithubRepoBinding> alvos = projectId == null
                    ? bindings
                    : bindings.stream().filter(b -> projectId.equals(b.getProjectId())).toList();

            for (GithubRepoBinding b : alvos) {
                b.setLastSyncAt(null);
                githubRepoBindingRepository.save(b);
                log.info("Repair GitHub: watermark zerado para org={} repo={}", organizationId, b.getRepoId());
            }

            /* O sync ja registra em integration_sync_logs e ja e idempotente por upsert. */
            triggerGithubSyncUseCase.execute();
        } finally {
            TenantContextHolder.clear();
        }
    }
}
