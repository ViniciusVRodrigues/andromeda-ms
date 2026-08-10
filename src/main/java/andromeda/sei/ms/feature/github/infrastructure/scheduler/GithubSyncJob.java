package andromeda.sei.ms.feature.github.infrastructure.scheduler;

import andromeda.sei.ms.feature.github.application.usecase.SyncGithubRepoDataUseCase;
import andromeda.sei.ms.feature.github.domain.GithubConnection;
import andromeda.sei.ms.feature.github.domain.GithubConnectionRepository;
import andromeda.sei.ms.feature.github.domain.GithubConnectionStatus;
import andromeda.sei.ms.feature.github.domain.GithubRepoBinding;
import andromeda.sei.ms.feature.github.domain.GithubRepoBindingRepository;
import andromeda.sei.ms.feature.integration.domain.SyncTrigger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Instant;
import java.util.List;

/**
 * Sequential loop -- connections (CONNECTED, active) -> repo bindings (syncEnabled, active) ->
 * sync steps -- mirroring {@code AzureSyncJob}'s structure exactly (design.md D1), including its
 * known permanent-trap circuit-breaker caveat (design.md D2): once {@code consecutiveFailures >= 3}
 * flips a connection to {@code ERROR}, the scheduler's own query (status == CONNECTED) excludes it
 * from all future runs, so recovery requires a manual reconnect. Replicated as-is for consistency
 * with the existing Azure behavior; not fixed here per design.md's explicit scope decision.
 *
 * <p>Sequential (not parallel) repo processing is deliberate here beyond mere simplicity, unlike
 * Azure: GitHub's secondary/abuse rate limits are explicitly triggered by request concurrency, so
 * sequential processing is the safer choice, not just the simpler one (design.md D1).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GithubSyncJob {

    private static final int MAX_CONSECUTIVE_FAILURES = 3;

    private final GithubConnectionRepository githubConnectionRepository;
    private final GithubRepoBindingRepository githubRepoBindingRepository;
    private final SyncGithubRepoDataUseCase syncGithubRepoDataUseCase;

    @Scheduled(cron = "0 */30 * * * *")
    public void run() {
        List<GithubConnection> connections = githubConnectionRepository
                .findAllByStatusAndActiveTrue(GithubConnectionStatus.CONNECTED);

        for (GithubConnection connection : connections) {
            syncOrganization(connection);
        }
    }

    private void syncOrganization(GithubConnection connection) {
        List<GithubRepoBinding> bindings = githubRepoBindingRepository
                .findAllByOrganizationIdAndSyncEnabledTrueAndActiveTrue(connection.getOrganizationId());

        boolean anyFailure = false;
        for (GithubRepoBinding binding : bindings) {
            String orgId = connection.getOrganizationId();
            String projectId = binding.getProjectId();
            try {
                syncGithubRepoDataUseCase.execute(connection, binding, SyncTrigger.SCHEDULED);
            } catch (WebClientResponseException e) {
                log.error("GitHub sync failed for org={} project={} httpStatus={} githubError={}",
                        orgId, projectId, e.getStatusCode().value(), e.getResponseBodyAsString(), e);
                anyFailure = true;
            } catch (Exception e) {
                log.error("GitHub sync failed for org={} project={}", orgId, projectId, e);
                anyFailure = true;
            }
        }

        if (anyFailure) {
            incrementFailures(connection);
        } else {
            resetFailures(connection);
        }
    }

    private void incrementFailures(GithubConnection connection) {
        connection.setConsecutiveFailures(connection.getConsecutiveFailures() + 1);
        if (connection.getConsecutiveFailures() >= MAX_CONSECUTIVE_FAILURES) {
            connection.setStatus(GithubConnectionStatus.ERROR);
        }
        githubConnectionRepository.save(connection);
    }

    private void resetFailures(GithubConnection connection) {
        connection.setConsecutiveFailures(0);
        connection.setLastSuccessAt(Instant.now());
        if (connection.getStatus() == GithubConnectionStatus.ERROR) {
            connection.setStatus(GithubConnectionStatus.CONNECTED);
        }
        githubConnectionRepository.save(connection);
    }
}
