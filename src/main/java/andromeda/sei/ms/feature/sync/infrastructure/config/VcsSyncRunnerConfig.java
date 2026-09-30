package andromeda.sei.ms.feature.sync.infrastructure.config;

import andromeda.sei.ms.feature.sync.application.ConnectionHealthUpdater;
import andromeda.sei.ms.feature.sync.application.runner.VcsBindingSyncRunner;
import andromeda.sei.ms.feature.vcs.application.port.VcsProviderRegistry;
import andromeda.sei.ms.feature.vcs.application.usecase.SyncRepositoryUseCase;
import andromeda.sei.ms.feature.vcs.domain.VcsProvider;
import andromeda.sei.ms.feature.vcs.domain.VcsRepoBindingRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * One repository sync runner per VCS provider (azure-repos-provider task 5.5/6.1). The
 * orchestrator dispatches by (integration, kind), so GitHub and Azure Repos never collide with
 * each other nor with the Boards runner.
 */
@Configuration
public class VcsSyncRunnerConfig {

    @Bean
    public VcsBindingSyncRunner githubRepositorySyncRunner(VcsProviderRegistry providerRegistry,
                                                           VcsRepoBindingRepository bindingRepository,
                                                           SyncRepositoryUseCase syncRepositoryUseCase,
                                                           ConnectionHealthUpdater connectionHealthUpdater) {
        return new VcsBindingSyncRunner(VcsProvider.GITHUB, providerRegistry, bindingRepository,
                syncRepositoryUseCase, connectionHealthUpdater);
    }

    @Bean
    public VcsBindingSyncRunner azureReposSyncRunner(VcsProviderRegistry providerRegistry,
                                                     VcsRepoBindingRepository bindingRepository,
                                                     SyncRepositoryUseCase syncRepositoryUseCase,
                                                     ConnectionHealthUpdater connectionHealthUpdater) {
        return new VcsBindingSyncRunner(VcsProvider.AZURE_REPOS, providerRegistry, bindingRepository,
                syncRepositoryUseCase, connectionHealthUpdater);
    }
}
