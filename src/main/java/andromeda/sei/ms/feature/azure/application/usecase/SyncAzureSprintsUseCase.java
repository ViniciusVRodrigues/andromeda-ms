package andromeda.sei.ms.feature.azure.application.usecase;

import andromeda.sei.ms.feature.azure.domain.AzureConnection;
import andromeda.sei.ms.feature.azure.domain.AzureProjectBinding;
import andromeda.sei.ms.feature.azure.domain.AzureProjectBindingRepository;
import andromeda.sei.ms.feature.azure.infrastructure.client.AdoIterationDto;
import andromeda.sei.ms.feature.azure.infrastructure.client.AzureAuthStrategyResolver;
import andromeda.sei.ms.feature.azure.infrastructure.client.AzureDevOpsClient;
import andromeda.sei.ms.feature.integration.domain.Integration;
import andromeda.sei.ms.feature.integration.domain.IntegrationSyncLog;
import andromeda.sei.ms.feature.integration.domain.IntegrationSyncLogRepository;
import andromeda.sei.ms.feature.integration.domain.SyncOperation;
import andromeda.sei.ms.feature.integration.domain.SyncStatus;
import andromeda.sei.ms.feature.integration.domain.SyncTrigger;
import andromeda.sei.ms.feature.workitem.domain.Sprint;
import andromeda.sei.ms.feature.workitem.domain.SprintRepository;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

@Slf4j
@Component
@RequiredArgsConstructor
public class SyncAzureSprintsUseCase {

    private final IntegrationSyncLogRepository syncLogRepository;
    private final AzureAuthStrategyResolver authStrategyResolver;
    private final SprintRepository sprintRepository;
    private final AzureDevOpsClient azureDevOpsClient;
    private final AzureProjectBindingRepository azureProjectBindingRepository;

    public void execute(AzureConnection connection, AzureProjectBinding binding, SyncTrigger trigger) {
        Instant startedAt = Instant.now();
        IntegrationSyncLog syncLog = new IntegrationSyncLog(
                null, connection.getOrganizationId(), Integration.AZURE_DEVOPS,
                SyncOperation.SYNC_SPRINTS, SyncStatus.RUNNING, trigger,
                binding.getProjectId(), startedAt, null, null, 0, 0, null, null);
        syncLog = syncLogRepository.save(syncLog);

        int processed = 0;
        int failed = 0;

        try {
            String orgUrl = connection.getAzureOrgUrl();
            String authHeader = authStrategyResolver.resolve(connection);
            String orgId = connection.getOrganizationId();
            String projectId = binding.getProjectId();
            String azureProjectId = binding.getAzureProjectId();

            var teams = azureDevOpsClient.getTeams(orgUrl, azureProjectId, authHeader);
            log.info("Sprint sync for org={} project={}: {} teams found", orgId, projectId, teams.size());

            for (var team : teams) {
                var iterations = azureDevOpsClient.getIterations(orgUrl, azureProjectId, team.id(), authHeader);
                for (var iteration : iterations) {
                    try {
                        upsertSprint(orgId, projectId, team.id(), team.name(), iteration);
                        processed++;
                    } catch (Exception e) {
                        log.warn("Failed to upsert sprint externalId={} teamId={}: {}",
                                iteration.id(), team.id(), e.getMessage());
                        failed++;
                    }
                }
            }

            binding.setLastSyncAt(Instant.now());
            azureProjectBindingRepository.save(binding);

            Instant finishedAt = Instant.now();
            SyncStatus status = processed > 0 || failed == 0 ? SyncStatus.SUCCESS : SyncStatus.FAILED;
            syncLog.setStatus(status);
            syncLog.setFinishedAt(finishedAt);
            syncLog.setDurationMs(finishedAt.toEpochMilli() - startedAt.toEpochMilli());
            syncLog.setItemsProcessed(processed);
            syncLog.setItemsFailed(failed);
            syncLogRepository.save(syncLog);
        } catch (Exception e) {
            Instant finishedAt = Instant.now();
            syncLog.setStatus(SyncStatus.FAILED);
            syncLog.setFinishedAt(finishedAt);
            syncLog.setDurationMs(finishedAt.toEpochMilli() - startedAt.toEpochMilli());
            syncLog.setItemsProcessed(processed);
            syncLog.setItemsFailed(failed);
            syncLog.setErrorCode("AZURE_SYNC_SPRINTS_FAILED");
            syncLog.setErrorMessage(e.getMessage());
            if (e instanceof WebClientResponseException wex) {
                syncLog.setHttpStatus(wex.getStatusCode().value());
                String body = wex.getResponseBodyAsString();
                syncLog.setErrorDetail(body.length() > 2000 ? body.substring(0, 2000) : body);
                log.error("Azure API HTTP error during sprint sync: status={} body={}",
                        wex.getStatusCode().value(), syncLog.getErrorDetail());
            }
            syncLogRepository.save(syncLog);
            throw e;
        }
    }

    private void upsertSprint(String orgId, String projectId, String teamId, String teamName,
                               AdoIterationDto iteration) {
        var existing = sprintRepository.findByOrganizationIdAndExternalIdAndTeamId(orgId, iteration.id(), teamId);
        Instant now = Instant.now();

        Instant startDate = parseDate(iteration.attributes() != null ? iteration.attributes().startDate() : null);
        Instant endDate = parseDate(iteration.attributes() != null ? iteration.attributes().finishDate() : null);
        Sprint.TimeFrame timeFrame = parseTimeFrame(
                iteration.attributes() != null ? iteration.attributes().timeFrame() : null);

        Sprint sprint = existing.orElseGet(() -> new Sprint(
                null, orgId, projectId, WorkItem.Source.AZURE_DEVOPS,
                iteration.id(), teamId, teamName, iteration.name(), iteration.path(),
                startDate, endDate, timeFrame, true, null, now, now));

        sprint.setTeamName(teamName);
        sprint.setName(iteration.name());
        sprint.setPath(iteration.path());
        sprint.setStartDate(startDate);
        sprint.setEndDate(endDate);
        sprint.setTimeFrame(timeFrame);

        sprintRepository.save(sprint);
    }

    private Instant parseDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        try {
            return LocalDate.parse(dateStr.substring(0, 10)).atStartOfDay().toInstant(ZoneOffset.UTC);
        } catch (Exception e) {
            return null;
        }
    }

    private Sprint.TimeFrame parseTimeFrame(String tf) {
        if (tf == null) return Sprint.TimeFrame.PAST;
        return switch (tf.toLowerCase()) {
            case "current" -> Sprint.TimeFrame.CURRENT;
            case "future" -> Sprint.TimeFrame.FUTURE;
            default -> Sprint.TimeFrame.PAST;
        };
    }
}
