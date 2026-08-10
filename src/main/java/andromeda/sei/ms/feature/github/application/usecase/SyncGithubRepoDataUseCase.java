package andromeda.sei.ms.feature.github.application.usecase;

import andromeda.sei.ms.feature.github.domain.GithubConnection;
import andromeda.sei.ms.feature.github.domain.GithubPullRequest;
import andromeda.sei.ms.feature.github.domain.GithubRepo;
import andromeda.sei.ms.feature.github.domain.GithubRepoBinding;
import andromeda.sei.ms.feature.github.domain.GithubRepoBindingRepository;
import andromeda.sei.ms.feature.integration.domain.Integration;
import andromeda.sei.ms.feature.integration.domain.IntegrationSyncLog;
import andromeda.sei.ms.feature.integration.domain.IntegrationSyncLogRepository;
import andromeda.sei.ms.feature.integration.domain.SyncOperation;
import andromeda.sei.ms.feature.integration.domain.SyncStatus;
import andromeda.sei.ms.feature.integration.domain.SyncTrigger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Instant;
import java.util.List;

/**
 * Orchestrates one repo binding's full sync -- repos -> commits -> pull requests -> reviews --
 * wrapped in the two-write {@link IntegrationSyncLog} lifecycle (mirrors
 * {@code SyncAzureWorkItemsUseCase}): insert with {@code status=RUNNING} at start, update to a
 * terminal status at end, and advance the binding's {@code lastSyncAt} watermark on success.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SyncGithubRepoDataUseCase {

    private final IntegrationSyncLogRepository syncLogRepository;
    private final GithubRepoBindingRepository githubRepoBindingRepository;
    private final SyncGithubReposUseCase syncGithubReposUseCase;
    private final SyncGithubCommitsUseCase syncGithubCommitsUseCase;
    private final SyncGithubPullRequestsUseCase syncGithubPullRequestsUseCase;
    private final SyncGithubReviewsUseCase syncGithubReviewsUseCase;
    private final CalculatePrMetricsUseCase calculatePrMetricsUseCase;
    private final CalculateRepoMetricsUseCase calculateRepoMetricsUseCase;

    public void execute(GithubConnection connection, GithubRepoBinding binding, SyncTrigger trigger) {
        Instant startedAt = Instant.now();
        Instant lastSyncAt = binding.getLastSyncAt();

        IntegrationSyncLog syncLog = new IntegrationSyncLog(
                null, connection.getOrganizationId(), Integration.GITHUB,
                SyncOperation.SYNC_REPOS, SyncStatus.RUNNING, trigger,
                binding.getProjectId(), startedAt, null, null, 0, 0, null, null);
        syncLog = syncLogRepository.save(syncLog);

        int processed = 0;
        try {
            GithubRepo repo = syncGithubReposUseCase.execute(connection, binding);
            if (repo == null) {
                finishSuccess(syncLog, startedAt, 0, 0);
                return;
            }
            processed++;

            int commitsProcessed = syncGithubCommitsUseCase.execute(connection, repo, lastSyncAt);
            List<GithubPullRequest> pullRequests =
                    syncGithubPullRequestsUseCase.execute(connection, repo, lastSyncAt);
            int reviewsProcessed = syncGithubReviewsUseCase.execute(connection, repo, pullRequests, lastSyncAt);

            processed += commitsProcessed + pullRequests.size() + reviewsProcessed;

            binding.setLastSyncAt(Instant.now());
            githubRepoBindingRepository.save(binding);

            // task 7.4: metrics calculation is isolated -- a failure here must not affect the
            // sync run's own success/failure status, matching CalculateWorkItemMetricsUseCase /
            // CalculateSprintMetricsUseCase's isolation pattern in AzureSyncJob.
            try {
                calculatePrMetricsUseCase.execute(connection.getOrganizationId(), repo.getId());
            } catch (Exception e) {
                log.error("PR metrics calculation failed for org={} repo={}",
                        connection.getOrganizationId(), repo.getFullName(), e);
            }
            try {
                calculateRepoMetricsUseCase.execute(connection, repo);
            } catch (Exception e) {
                log.error("Repo metrics (CFR) calculation failed for org={} repo={}",
                        connection.getOrganizationId(), repo.getFullName(), e);
            }

            finishSuccess(syncLog, startedAt, processed, 0);
            log.info("GitHub sync completed: org={} project={} repo={} processed={}",
                    connection.getOrganizationId(), binding.getProjectId(), repo.getFullName(), processed);
        } catch (Exception e) {
            Instant finishedAt = Instant.now();
            syncLog.setStatus(SyncStatus.FAILED);
            syncLog.setFinishedAt(finishedAt);
            syncLog.setDurationMs(finishedAt.toEpochMilli() - startedAt.toEpochMilli());
            syncLog.setItemsProcessed(processed);
            syncLog.setItemsFailed(1);
            syncLog.setErrorCode("GITHUB_SYNC_FAILED");
            syncLog.setErrorMessage(e.getMessage());
            if (e instanceof WebClientResponseException wex) {
                syncLog.setHttpStatus(wex.getStatusCode().value());
                String body = wex.getResponseBodyAsString();
                syncLog.setErrorDetail(body.length() > 2000 ? body.substring(0, 2000) : body);
                log.error("GitHub API HTTP error during sync: status={} body={}",
                        wex.getStatusCode().value(), syncLog.getErrorDetail());
            }
            syncLogRepository.save(syncLog);
            throw e;
        }
    }

    private void finishSuccess(IntegrationSyncLog syncLog, Instant startedAt, int processed, int failed) {
        Instant finishedAt = Instant.now();
        syncLog.setStatus(SyncStatus.SUCCESS);
        syncLog.setFinishedAt(finishedAt);
        syncLog.setDurationMs(finishedAt.toEpochMilli() - startedAt.toEpochMilli());
        syncLog.setItemsProcessed(processed);
        syncLog.setItemsFailed(failed);
        syncLogRepository.save(syncLog);
    }
}
