package andromeda.sei.ms.feature.github.application.usecase;

import andromeda.sei.ms.feature.github.domain.GithubConnection;
import andromeda.sei.ms.feature.github.domain.GithubConnectionRepository;
import andromeda.sei.ms.feature.github.domain.GithubRepoBinding;
import andromeda.sei.ms.feature.github.domain.GithubRepoBindingRepository;
import andromeda.sei.ms.feature.github.domain.exception.GithubConnectionNotFoundException;
import andromeda.sei.ms.feature.integration.domain.SyncTrigger;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Manual GitHub sync trigger (design.md D7), mirroring {@code TriggerAzureSyncUseCase}: requires
 * an active connection, syncs every {@code syncEnabled && active} binding with
 * {@link SyncTrigger#MANUAL}, isolating per-binding failures so one repo's failure doesn't abort
 * the rest. Does NOT touch {@code consecutiveFailures}/{@code lastSuccessAt} — the
 * circuit-breaker state is owned by the scheduled job, same as Azure's manual path.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TriggerGithubSyncUseCase {

    private final GithubConnectionRepository githubConnectionRepository;
    private final GithubRepoBindingRepository githubRepoBindingRepository;
    private final SyncGithubRepoDataUseCase syncGithubRepoDataUseCase;

    public void execute() {
        String orgId = TenantContextHolder.get();

        GithubConnection connection = githubConnectionRepository
                .findByOrganizationIdAndActiveTrue(orgId)
                .orElseThrow(() -> new GithubConnectionNotFoundException(orgId));

        List<GithubRepoBinding> bindings = githubRepoBindingRepository
                .findAllByOrganizationIdAndSyncEnabledTrueAndActiveTrue(orgId);

        for (GithubRepoBinding binding : bindings) {
            try {
                syncGithubRepoDataUseCase.execute(connection, binding, SyncTrigger.MANUAL);
            } catch (Exception e) {
                log.error("Manual GitHub sync failed for org={} project={} repo={}",
                        orgId, binding.getProjectId(), binding.getRepoId(), e);
            }
        }
    }
}
