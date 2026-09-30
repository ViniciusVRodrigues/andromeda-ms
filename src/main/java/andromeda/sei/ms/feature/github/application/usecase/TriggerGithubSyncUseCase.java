package andromeda.sei.ms.feature.github.application.usecase;

import andromeda.sei.ms.feature.github.domain.GithubConnectionRepository;
import andromeda.sei.ms.feature.github.domain.exception.GithubConnectionNotFoundException;
import andromeda.sei.ms.feature.integration.domain.Integration;
import andromeda.sei.ms.feature.integration.domain.SyncTrigger;
import andromeda.sei.ms.feature.sync.application.SyncOrchestrator;
import andromeda.sei.ms.feature.sync.application.SyncRequest;
import andromeda.sei.ms.feature.sync.application.SyncTargetResolver;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Sync manual do GitHub, assincrono (on-demand-refresh): exige conexao ativa e submete todo
 * binding {@code syncEnabled && active} ao {@link SyncOrchestrator} com {@link SyncTrigger#MANUAL},
 * devolvendo o {@code syncId} antes de qualquer chamada externa. O orquestrador isola falhas por
 * binding, aplica o cooldown por binding e atualiza a saude da conexao (recuperando {@code ERROR}).
 */
@Component
@RequiredArgsConstructor
public class TriggerGithubSyncUseCase {

    private final GithubConnectionRepository githubConnectionRepository;
    private final SyncTargetResolver syncTargetResolver;
    private final SyncOrchestrator syncOrchestrator;

    /**
     * @return {@code syncId} do sync (log pai {@code ORGANIZATION_SYNC})
     * @throws GithubConnectionNotFoundException sem conexao ativa
     */
    public String execute() {
        String orgId = TenantContextHolder.get();
        githubConnectionRepository.findByOrganizationIdAndActiveTrue(orgId)
                .orElseThrow(() -> new GithubConnectionNotFoundException(orgId));

        return syncOrchestrator.submit(SyncRequest.organization(orgId, Integration.GITHUB,
                syncTargetResolver.forOrganization(orgId, Integration.GITHUB, null), SyncTrigger.MANUAL));
    }
}
