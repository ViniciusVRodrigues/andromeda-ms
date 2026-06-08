package andromeda.sei.ms.feature.azure.application.usecase;

import andromeda.sei.ms.feature.azure.application.DefaultWorkflowConfigFactory;
import andromeda.sei.ms.feature.azure.domain.AzureConnection;
import andromeda.sei.ms.feature.azure.domain.AzureProjectBinding;
import andromeda.sei.ms.feature.azure.domain.StatusMapping;
import andromeda.sei.ms.feature.azure.domain.WorkflowConfig;
import andromeda.sei.ms.feature.azure.infrastructure.client.AdoWorkItemDto;
import andromeda.sei.ms.feature.azure.infrastructure.client.AdoWorkItemUpdateDto;
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
import andromeda.sei.ms.feature.workitem.domain.WorkItemRepository;
import andromeda.sei.ms.feature.workitem.domain.WorkItemTransition;
import andromeda.sei.ms.feature.workitem.domain.WorkItemTransitionRepository;
import andromeda.sei.ms.shared.infrastructure.security.EncryptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class SyncAzureWorkItemsUseCase {

    private final IntegrationSyncLogRepository syncLogRepository;
    private final EncryptionService encryptionService;
    private final WorkItemRepository workItemRepository;
    private final WorkItemTransitionRepository workItemTransitionRepository;
    private final SprintRepository sprintRepository;
    private final AzureDevOpsClient azureDevOpsClient;

    public void execute(AzureConnection connection, AzureProjectBinding binding, SyncTrigger trigger) {
        Instant startedAt = Instant.now();
        IntegrationSyncLog syncLog = new IntegrationSyncLog(
                null, connection.getOrganizationId(), Integration.AZURE_DEVOPS,
                SyncOperation.SYNC_WORK_ITEMS, SyncStatus.RUNNING, trigger,
                binding.getProjectId(), startedAt, null, null, 0, 0, null, null);
        syncLog = syncLogRepository.save(syncLog);

        int processed = 0;
        int failed = 0;

        try {
            String orgUrl = connection.getAzureOrgUrl();
            String accessToken = encryptionService.decrypt(connection.getAccessToken());
            String orgId = connection.getOrganizationId();
            String projectId = binding.getProjectId();
            String azureProjectId = binding.getAzureProjectId();

            WorkflowConfig config = binding.getWorkflowConfig() != null
                    ? binding.getWorkflowConfig()
                    : DefaultWorkflowConfigFactory.fallback();

            boolean fullSync = binding.getLastSyncAt() == null;
            log.info("Work item sync for org={} project={} mode={}", orgId, projectId,
                    fullSync ? "FULL" : "INCREMENTAL");

            List<Integer> ids = azureDevOpsClient.queryWorkItemIds(
                    orgUrl, azureProjectId, accessToken, binding.getLastSyncAt());

            if (ids.isEmpty()) {
                log.info("No work items to sync for org={} project={}", orgId, projectId);
                finishSuccess(syncLog, startedAt, 0, 0);
                binding.setLastSyncAt(Instant.now());
                return;
            }

            List<AdoWorkItemDto> adoItems = azureDevOpsClient.getWorkItemsBatch(orgUrl, ids, accessToken);

            for (AdoWorkItemDto adoItem : adoItems) {
                try {
                    processWorkItem(orgId, projectId, adoItem, config, orgUrl, accessToken, fullSync);
                    processed++;
                } catch (Exception e) {
                    log.warn("Failed to process work item id={}: {}", adoItem.id(), e.getMessage());
                    failed++;
                }
            }

            binding.setLastSyncAt(Instant.now());

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
            syncLog.setErrorCode("AZURE_SYNC_WORK_ITEMS_FAILED");
            syncLog.setErrorMessage(e.getMessage());
            syncLogRepository.save(syncLog);
            throw e;
        }
    }

    private void processWorkItem(String orgId, String projectId, AdoWorkItemDto adoItem,
                                  WorkflowConfig config, String orgUrl, String accessToken,
                                  boolean fullSync) {
        Map<String, Object> fields = adoItem.fields() != null ? adoItem.fields() : Map.of();
        String externalId = String.valueOf(adoItem.id());

        Optional<WorkItem> existing = workItemRepository.findByOrganizationIdAndExternalIdAndSource(
                orgId, externalId, WorkItem.Source.AZURE_DEVOPS);
        // Capture stored status before any mutations for incremental transition comparison
        String storedRawStatus = existing.map(WorkItem::getRawStatus).orElse(null);

        String rawType = getString(fields, "System.WorkItemType");
        String rawStatus = getString(fields, "System.State");
        WorkItem.ResolvedType resolvedType = resolveType(rawType, config);
        WorkItem.ResolvedStatus resolvedStatus = resolveStatus(rawStatus, config);

        String iterationId = getString(fields, "System.IterationId");
        String sprintId = resolveSprintId(orgId, iterationId);

        String tagsRaw = getString(fields, "System.Tags");
        List<String> labels = tagsRaw != null
                ? List.of(tagsRaw.split(";")).stream().map(String::trim).filter(s -> !s.isBlank()).toList()
                : List.of();

        Instant now = Instant.now();

        WorkItem workItem = existing.orElseGet(() -> new WorkItem(
                null, orgId, projectId, WorkItem.Source.AZURE_DEVOPS,
                externalId, null, null, null, null, null, null,
                null, null, null, null, null, null,
                null, null, null, null, null, true, null, now));

        workItem.setProjectId(projectId);
        workItem.setExternalUpdatedAt(parseInstant(fields, "System.ChangedDate"));
        workItem.setTitle(getString(fields, "System.Title"));
        workItem.setRawType(rawType);
        workItem.setResolvedType(resolvedType);
        workItem.setRawStatus(rawStatus);
        workItem.setResolvedStatus(resolvedStatus);
        workItem.setPriority(getString(fields, "Microsoft.VSTS.Common.Priority"));
        workItem.setStoryPoints(getDouble(fields, "Microsoft.VSTS.Scheduling.StoryPoints"));
        workItem.setLabels(labels);
        workItem.setSprintId(sprintId);
        workItem.setAssigneeExternalId(getAssigneeId(fields));
        if (workItem.getCreatedAt() == null) {
            workItem.setCreatedAt(parseInstant(fields, "System.CreatedDate"));
        }

        // Save first to ensure workItemId is set before building transitions
        WorkItem saved = workItemRepository.save(workItem);

        buildTransitions(saved, storedRawStatus, adoItem, config, orgUrl, accessToken);

        List<WorkItemTransition> transitions = workItemTransitionRepository.findAllByWorkItemId(saved.getId());
        saved.setStartedAt(deriveStartedAt(transitions));
        saved.setCompletedAt(deriveCompletedAt(transitions));
        workItemRepository.save(saved);
    }

    private void buildTransitions(WorkItem workItem, String storedRawStatus, AdoWorkItemDto adoItem,
                                   WorkflowConfig config, String orgUrl, String accessToken) {
        String workItemId = workItem.getId();
        if (workItemId == null) return;

        boolean hasTransitions = workItemTransitionRepository.existsByWorkItemId(workItemId);

        if (!hasTransitions) {
            buildFullTransitionHistory(workItemId, workItem.getOrganizationId(), adoItem,
                    config, orgUrl, accessToken, workItem.getRawStatus(),
                    workItem.getCreatedAt());
        } else {
            appendIncrementalTransition(workItemId, workItem.getOrganizationId(),
                    storedRawStatus, adoItem, config);
        }
    }

    private void buildFullTransitionHistory(String workItemId, String orgId, AdoWorkItemDto adoItem,
                                             WorkflowConfig config, String orgUrl, String accessToken,
                                             String currentRawStatus, Instant createdAt) {
        List<AdoWorkItemUpdateDto> updates = azureDevOpsClient.getWorkItemUpdates(orgUrl, adoItem.id(), accessToken);

        List<WorkItemTransition> transitions = new ArrayList<>();
        String prevStatus = null;

        for (AdoWorkItemUpdateDto update : updates) {
            if (update.fields() == null) continue;
            Object stateChange = update.fields().get("System.State");
            if (stateChange == null) continue;

            @SuppressWarnings("unchecked")
            Map<String, Object> stateField = (Map<String, Object>) stateChange;
            String newVal = getString(stateField, "newValue");
            if (newVal == null) continue;

            Instant occurredAt = update.revisedDate() != null
                    ? Instant.parse(update.revisedDate()) : Instant.now();

            WorkItem.ResolvedStatus resolved = resolveStatus(newVal, config);
            transitions.add(new WorkItemTransition(
                    null, orgId, workItemId, prevStatus, newVal, resolved,
                    occurredAt, Instant.now(), true, null, Instant.now(), Instant.now()));
            prevStatus = newVal;
        }

        if (transitions.isEmpty()) {
            WorkItem.ResolvedStatus resolved = resolveStatus(currentRawStatus, config);
            transitions.add(new WorkItemTransition(
                    null, orgId, workItemId, null, currentRawStatus, resolved,
                    createdAt != null ? createdAt : Instant.now(),
                    Instant.now(), true, null, Instant.now(), Instant.now()));
        }

        for (WorkItemTransition t : transitions) {
            workItemTransitionRepository.upsertByCompositeKey(t);
        }
    }

    private void appendIncrementalTransition(String workItemId, String orgId,
                                              String storedRawStatus, AdoWorkItemDto adoItem,
                                              WorkflowConfig config) {
        if (storedRawStatus == null) return;
        Map<String, Object> fields = adoItem.fields() != null ? adoItem.fields() : Map.of();
        String incomingStatus = getString(fields, "System.State");
        String storedStatus = storedRawStatus;

        if (incomingStatus == null || incomingStatus.equals(storedStatus)) return;

        Instant occurredAt = parseInstant(fields, "System.ChangedDate");
        if (occurredAt == null) occurredAt = Instant.now();

        WorkItem.ResolvedStatus resolved = resolveStatus(incomingStatus, config);
        WorkItemTransition transition = new WorkItemTransition(
                null, orgId, workItemId, storedStatus, incomingStatus, resolved,
                occurredAt, Instant.now(), true, null, Instant.now(), Instant.now());
        workItemTransitionRepository.upsertByCompositeKey(transition);
    }

    private Instant deriveStartedAt(List<WorkItemTransition> transitions) {
        return transitions.stream()
                .filter(t -> t.getToResolvedStatus() == WorkItem.ResolvedStatus.IN_PROGRESS)
                .map(WorkItemTransition::getOccurredAt)
                .min(Comparator.naturalOrder())
                .orElse(null);
    }

    private Instant deriveCompletedAt(List<WorkItemTransition> transitions) {
        return transitions.stream()
                .filter(t -> t.getToResolvedStatus() == WorkItem.ResolvedStatus.DONE)
                .map(WorkItemTransition::getOccurredAt)
                .min(Comparator.naturalOrder())
                .orElse(null);
    }

    private WorkItem.ResolvedType resolveType(String rawType, WorkflowConfig config) {
        if (rawType == null || config == null || config.getTypeMappings() == null) {
            return WorkItem.ResolvedType.TASK;
        }
        return config.getTypeMappings().stream()
                .filter(m -> rawType.equalsIgnoreCase(m.getRawType()))
                .map(m -> m.getResolvedType())
                .findFirst()
                .orElse(WorkItem.ResolvedType.TASK);
    }

    private WorkItem.ResolvedStatus resolveStatus(String rawStatus, WorkflowConfig config) {
        if (rawStatus == null || config == null || config.getStatusMappings() == null) {
            return WorkItem.ResolvedStatus.TODO;
        }
        return config.getStatusMappings().stream()
                .filter(m -> rawStatus.equalsIgnoreCase(m.getRawStatus()))
                .map(StatusMapping::getResolvedStatus)
                .findFirst()
                .orElse(WorkItem.ResolvedStatus.TODO);
    }

    private String resolveSprintId(String orgId, String iterationId) {
        if (iterationId == null) return null;
        return sprintRepository.findByOrganizationIdAndExternalId(orgId, iterationId)
                .map(Sprint::getId)
                .orElse(null);
    }

    private String getString(Map<String, Object> fields, String key) {
        Object val = fields.get(key);
        return val != null ? val.toString() : null;
    }

    private Double getDouble(Map<String, Object> fields, String key) {
        Object val = fields.get(key);
        if (val == null) return null;
        try { return ((Number) val).doubleValue(); } catch (Exception e) { return null; }
    }

    private Instant parseInstant(Map<String, Object> fields, String key) {
        String val = getString(fields, key);
        if (val == null) return null;
        try { return Instant.parse(val); } catch (Exception e) { return null; }
    }

    private String getAssigneeId(Map<String, Object> fields) {
        Object val = fields.get("System.AssignedTo");
        if (val == null) return null;
        if (val instanceof Map<?, ?> identity) {
            Object id = identity.get("id");
            return id != null ? id.toString() : null;
        }
        return null;
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
