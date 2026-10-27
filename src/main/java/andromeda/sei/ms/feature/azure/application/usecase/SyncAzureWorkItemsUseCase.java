package andromeda.sei.ms.feature.azure.application.usecase;

import andromeda.sei.ms.feature.azure.application.DefaultWorkflowConfigFactory;
import andromeda.sei.ms.feature.azure.application.dto.ActivityDay;
import andromeda.sei.ms.feature.azure.application.dto.AzureWorkItemsSyncResult;
import andromeda.sei.ms.feature.azure.application.port.AzureOrgUserCollector;
import andromeda.sei.ms.feature.azure.application.service.AzureWorkItemRemovalReconciler;
import andromeda.sei.ms.feature.azure.domain.AzureConnection;
import andromeda.sei.ms.feature.azure.domain.AzureOrgUserSource;
import andromeda.sei.ms.feature.azure.domain.AzureProjectBinding;
import andromeda.sei.ms.feature.azure.domain.AzureProjectBindingRepository;
import andromeda.sei.ms.feature.azure.domain.FieldMapping;
import andromeda.sei.ms.feature.azure.domain.StatusMapping;
import andromeda.sei.ms.feature.azure.domain.WorkflowConfig;
import andromeda.sei.ms.feature.azure.domain.exception.AzureSyncPageLimitExceededException;
import andromeda.sei.ms.feature.azure.infrastructure.client.AdoWorkItemDto;
import andromeda.sei.ms.feature.azure.infrastructure.client.AdoWorkItemUpdateDto;
import andromeda.sei.ms.feature.azure.infrastructure.client.AzureAuthStrategyResolver;
import andromeda.sei.ms.feature.azure.infrastructure.client.AzureDevOpsClient;
import andromeda.sei.ms.feature.integration.domain.Integration;
import andromeda.sei.ms.feature.integration.domain.IntegrationSyncLog;
import andromeda.sei.ms.feature.integration.domain.IntegrationSyncLogRepository;
import andromeda.sei.ms.feature.integration.domain.SyncOperation;
import andromeda.sei.ms.feature.integration.domain.SyncStatus;
import andromeda.sei.ms.feature.integration.domain.SyncTrigger;
import andromeda.sei.ms.feature.workitem.domain.FieldValueSource;
import andromeda.sei.ms.feature.workitem.domain.SharedIterations;
import andromeda.sei.ms.feature.workitem.domain.Sprint;
import andromeda.sei.ms.feature.workitem.domain.SprintRepository;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.domain.WorkItemActivity;
import andromeda.sei.ms.feature.workitem.domain.WorkItemActivityRepository;
import andromeda.sei.ms.feature.workitem.domain.WorkItemRepository;
import andromeda.sei.ms.feature.workitem.domain.WorkItemTransition;
import andromeda.sei.ms.feature.workitem.domain.WorkItemTransitionRepository;
import andromeda.sei.ms.feature.user.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class SyncAzureWorkItemsUseCase {

    private final IntegrationSyncLogRepository syncLogRepository;
    private final AzureAuthStrategyResolver authStrategyResolver;
    private final WorkItemRepository workItemRepository;
    private final WorkItemTransitionRepository workItemTransitionRepository;
    private final SprintRepository sprintRepository;
    private final AzureDevOpsClient azureDevOpsClient;
    private final UserRepository userRepository;
    private final AzureProjectBindingRepository azureProjectBindingRepository;
    private final AzureOrgUserCollector orgUserCollector;
    private final AzureWorkItemRemovalReconciler removalReconciler;
    private final WorkItemActivityRepository workItemActivityRepository;

    /**
     * @return o binding tal como persistido, com a {@code version} atualizada. Chamadores
     *         que encadeiam syncs sobre o mesmo binding devem seguir com esta instância:
     *         reutilizar a original (versão já consumida por este save) faz o save seguinte
     *         falhar com OptimisticLockingFailureException.
     */
    public AzureProjectBinding execute(AzureConnection connection, AzureProjectBinding binding, SyncTrigger trigger) {
        return sync(connection, binding, trigger, null).binding();
    }

    /**
     * Sincroniza os work items do binding e devolve o desfecho completo.
     *
     * @param backfillFrom limite inferior do primeiro sync (quando o binding ainda nao tem
     *                     watermark): so work items alterados a partir dele sao buscados, via
     *                     {@code [System.ChangedDate] >= backfillFrom} no WIQL. {@code null}
     *                     busca o historico inteiro. Ignorado nos syncs incrementais.
     * @return binding persistido e status; {@code PARTIAL} quando algum item falhou
     */
    public AzureWorkItemsSyncResult sync(AzureConnection connection, AzureProjectBinding binding,
                                         SyncTrigger trigger, Instant backfillFrom) {
        Instant startedAt = Instant.now();
        IntegrationSyncLog syncLog = new IntegrationSyncLog(
                null, connection.getOrganizationId(), Integration.AZURE_DEVOPS,
                SyncOperation.SYNC_WORK_ITEMS, SyncStatus.RUNNING, trigger,
                binding.getProjectId(), startedAt, null, null, 0, 0, null, null);
        syncLog = syncLogRepository.save(syncLog);

        int processed = 0;
        int failed = 0;
        int deactivated = 0;
        int reactivated = 0;
        boolean reconciliationSkipped = false;
        ActivityCollector activity = new ActivityCollector();

        try {
            String orgUrl = connection.getAzureOrgUrl();
            String authHeader = authStrategyResolver.resolve(connection);
            String orgId = connection.getOrganizationId();
            String projectId = binding.getProjectId();
            String azureProjectId = binding.getAzureProjectId();

            WorkflowConfig config = binding.getWorkflowConfig() != null
                    ? binding.getWorkflowConfig()
                    : DefaultWorkflowConfigFactory.fallback();

            /* Marcador PROPRIO do sync de work items. Usar lastSyncAt aqui era o bug:
             * o sync de sprints roda antes e o carimba com now(), entao um binding
             * novo nunca via null e nascia incremental com since = hoje — importando
             * so o que mudou no dia. */
            Instant watermark = binding.getWorkItemsWatermark();
            boolean fullSync = watermark == null;
            /* Primeiro sync limitado pela janela de backfill da organizacao (design D6): sem
             * isso um projeto com anos de historico baixaria tudo de uma vez. */
            Instant since = fullSync ? backfillFrom : watermark;
            log.info("Work item sync for org={} project={} mode={} since={}", orgId, projectId,
                    fullSync ? "FULL" : "INCREMENTAL", since);

            List<Integer> ids = fetchAllWorkItemIds(orgUrl, azureProjectId, authHeader,
                    since, config);

            /* Reconciliacao de removidos (azure-itens-excluidos D1): o WIQL de IDs sem filtro de data
             * decide quem reativar (entra no lote) e, depois do loop, quem desativar. */
            Set<String> presentIds = (since == null
                    ? removalReconciler.presentIdsOf(ids, azureProjectId)
                    : removalReconciler.fetchPresentIds(orgUrl, azureProjectId, authHeader, config)).orElse(null);
            reconciliationSkipped = presentIds == null;
            List<Integer> batchIds = presentIds == null ? ids
                    : withReactivations(ids, removalReconciler.findReactivations(orgId, presentIds));

            List<AdoWorkItemDto> adoItems = batchIds.isEmpty()
                    ? List.of() : azureDevOpsClient.getWorkItemsBatch(orgUrl, batchIds, authHeader);

            Map<String, AzureOrgUserCollector.ObservedIdentity> seen = new java.util.LinkedHashMap<>();
            Set<String> processedIds = new java.util.HashSet<>();
            for (AdoWorkItemDto adoItem : adoItems) {
                try {
                    if (processWorkItem(orgId, projectId, adoItem, config, orgUrl, authHeader, activity)) reactivated++;
                    processedIds.add(String.valueOf(adoItem.id()));
                    collectIdentities(adoItem, seen);
                    processed++;
                } catch (Exception e) {
                    log.warn("Failed to process work item id={}: {}", adoItem.id(), e.getMessage());
                    failed++;
                }
            }

            if (presentIds != null) {
                deactivated = removalReconciler.deactivateMissing(orgId, projectId, orgUrl, azureProjectId,
                        authHeader, presentIds, processedIds);
            }

            collectSeenIdentities(orgId, seen);

            binding.setLastSyncAt(Instant.now());
            /* startedAt, nao now(): item editado DURANTE o sync tem ChangedDate menor
             * que o instante final e cairia abaixo do watermark para sempre. */
            binding.setWorkItemsWatermark(startedAt);
            AzureProjectBinding saved = azureProjectBindingRepository.save(binding);

            Instant finishedAt = Instant.now();
            SyncStatus status = resolveStatus(processed, failed);
            syncLog.setStatus(status);
            syncLog.setFinishedAt(finishedAt);
            syncLog.setDurationMs(finishedAt.toEpochMilli() - startedAt.toEpochMilli());
            syncLog.setItemsProcessed(processed);
            syncLog.setItemsFailed(failed);
            syncLog.setItemsDeactivated(deactivated);
            syncLog.setItemsReactivated(reactivated);
            syncLogRepository.save(syncLog);
            log.info("Work item sync completed: org={} project={} status={} processed={} failed={} deactivated={} reactivated={} reconciliationSkipped={} activitiesStored={} durationMs={}",
                    connection.getOrganizationId(), binding.getProjectId(), status, processed, failed,
                    deactivated, reactivated, reconciliationSkipped, activity.stored,
                    finishedAt.toEpochMilli() - startedAt.toEpochMilli());
            return new AzureWorkItemsSyncResult(saved, status, processed, failed, deactivated, reactivated,
                    reconciliationSkipped, activity.days);

        } catch (Exception e) {
            Instant finishedAt = Instant.now();
            syncLog.setStatus(SyncStatus.FAILED);
            syncLog.setFinishedAt(finishedAt);
            syncLog.setDurationMs(finishedAt.toEpochMilli() - startedAt.toEpochMilli());
            syncLog.setItemsProcessed(processed);
            syncLog.setItemsFailed(failed);
            if (e instanceof AzureSyncPageLimitExceededException pageLimitEx) {
                syncLog.setErrorCode(pageLimitEx.getCode());
            } else {
                syncLog.setErrorCode("AZURE_SYNC_WORK_ITEMS_FAILED");
            }
            syncLog.setErrorMessage(e.getMessage());
            // When Reactor exhausts its retries it wraps the original response
            // exception, which is where the Azure DevOps error body lives.
            Throwable root = e;
            while (!(root instanceof WebClientResponseException) && root.getCause() != null
                    && root.getCause() != root) {
                root = root.getCause();
            }
            if (root instanceof WebClientResponseException wex) {
                syncLog.setHttpStatus(wex.getStatusCode().value());
                String body = wex.getResponseBodyAsString();
                syncLog.setErrorDetail(body.length() > 2000 ? body.substring(0, 2000) : body);
                log.error("Azure API HTTP error during work item sync: status={} body={}",
                        wex.getStatusCode().value(), syncLog.getErrorDetail());
            }
            syncLogRepository.save(syncLog);
            throw e;
        }
    }

    /**
     * Um sync so e {@code SUCCESS} quando nenhum item falhou. Reportar sucesso com itens
     * perdidos escondia do refresh (e do usuario) que o historico ficou incompleto.
     */
    private static SyncStatus resolveStatus(int processed, int failed) {
        if (failed == 0) return SyncStatus.SUCCESS;
        return processed > 0 ? SyncStatus.PARTIAL : SyncStatus.FAILED;
    }

    private List<Integer> fetchAllWorkItemIds(String orgUrl, String azureProjectId, String authHeader,
                                               Instant since, WorkflowConfig config) {
        return removalReconciler.fetchAllWorkItemIds(orgUrl, azureProjectId, authHeader, since, config);
    }

    /** Incremental ids followed by the reactivation ids not already in them. */
    private static List<Integer> withReactivations(List<Integer> ids, Set<String> reactivate) {
        if (reactivate.isEmpty()) return ids;
        Set<Integer> merged = new java.util.LinkedHashSet<>(ids);
        for (String externalId : reactivate) {
            try {
                merged.add(Integer.valueOf(externalId));
            } catch (NumberFormatException ignored) {
                // non-numeric externalId cannot come from Azure
            }
        }
        return new ArrayList<>(merged);
    }

    /** @return true when the item existed inactive and this write reactivated it */
    private boolean processWorkItem(String orgId, String projectId, AdoWorkItemDto adoItem,
                                  WorkflowConfig config, String orgUrl, String authHeader,
                                  ActivityCollector activity) {
        Map<String, Object> fields = adoItem.fields() != null ? adoItem.fields() : Map.of();
        String externalId = String.valueOf(adoItem.id());

        Optional<WorkItem> existing = workItemRepository.findByOrganizationIdAndExternalIdAndSource(
                orgId, externalId, WorkItem.Source.AZURE_DEVOPS);
        // Capture stored status before any mutations for incremental transition comparison
        String storedRawStatus = existing.map(WorkItem::getRawStatus).orElse(null);

        String rawType = getString(fields, "System.WorkItemType");
        String rawStatus = getString(fields, "System.State");
        WorkItem.ResolvedType resolvedType = resolveType(rawType, config);
        WorkItem.ResolvedStatus resolvedStatus = resolveStatus(rawType, rawStatus, config);

        String iterationPath = getString(fields, "System.IterationPath");
        Sprint sprint = resolveSprint(orgId, projectId, iterationPath);

        String tagsRaw = getString(fields, "System.Tags");
        List<String> labels = tagsRaw != null
                ? List.of(tagsRaw.split(";")).stream().map(String::trim).filter(s -> !s.isBlank()).toList()
                : List.of();

        Instant now = Instant.now();

        WorkItem workItem = existing.orElseGet(() -> new WorkItem(
                null, orgId, projectId, WorkItem.Source.AZURE_DEVOPS,
                externalId, null, null, null, null, null, null,
                null, null, null, null, null, null,
                null, null, null, null, null, null, true, null, now,
                null, null, null, null, null, null, null, null));

        boolean reactivated = existing.isPresent() && !existing.get().isActive();
        workItem.setActive(true);
        workItem.setDeactivatedAt(null);
        workItem.setDeactivationReason(null);
        workItem.setProjectId(projectId);
        workItem.setExternalUpdatedAt(parseInstant(fields, "System.ChangedDate"));
        workItem.setTitle(getString(fields, "System.Title"));
        workItem.setRawType(rawType);
        workItem.setResolvedType(resolvedType);
        workItem.setRawStatus(rawStatus);
        workItem.setResolvedStatus(resolvedStatus);
        workItem.setPriority(getString(fields, "Microsoft.VSTS.Common.Priority"));
        applyComplexityAndEffort(workItem, fields, config, rawType);
        workItem.setLabels(labels);
        workItem.setSprintId(sprint != null ? sprint.getId() : null);
        workItem.setSprintName(sprint != null ? sprint.getName() : null);
        workItem.setAssigneeExternalId(getAssigneeId(fields));
        workItem.setAssigneeEmail(getAssigneeEmail(fields));
        workItem.setParentExternalId(extractParentExternalId(adoItem));
        resolveAssigneeUserId(orgId, workItem);
        if (workItem.getCreatedAt() == null) {
            workItem.setCreatedAt(parseInstant(fields, "System.CreatedDate"));
        }

        // Seed startedAt / completedAt directly from ADO fields -- the transitions
        // history may be incomplete or unavailable, so these are authoritative fallbacks.
        Instant adoActivatedDate = parseInstant(fields, "Microsoft.VSTS.Common.ActivatedDate");
        Instant adoClosedDate = firstNonNull(
                parseInstant(fields, "Microsoft.VSTS.Common.ClosedDate"),
                parseInstant(fields, "Microsoft.VSTS.Common.ResolvedDate"));

        if (adoActivatedDate != null) workItem.setStartedAt(adoActivatedDate);
        if (adoClosedDate != null) workItem.setCompletedAt(adoClosedDate);

        // Save first to ensure workItemId is set before building transitions
        WorkItem saved = workItemRepository.save(workItem);

        List<AdoWorkItemUpdateDto> fetchedUpdates =
                buildTransitions(saved, storedRawStatus, adoItem, config, orgUrl, authHeader, rawType);
        /* Atividade (alocacao-no-dashboard D2): primeiro sync reaproveita os updates ja baixados para as
         * transicoes; nos demais (incremental, repair) busca e grava so o que passa da maior revisao. */
        recordActivity(saved, fetchedUpdates, orgUrl, authHeader, activity);

        // Re-derive from transitions -- prefer transition-derived timestamps (more granular)
        // but fall back to the ADO field values already set above if transitions give nothing.
        List<WorkItemTransition> transitions = workItemTransitionRepository.findAllByWorkItemId(saved.getId());
        Instant transitionStartedAt = deriveStartedAt(transitions);
        Instant transitionCompletedAt = deriveCompletedAt(transitions);
        if (transitionStartedAt != null) saved.setStartedAt(transitionStartedAt);
        if (transitionCompletedAt != null) saved.setCompletedAt(transitionCompletedAt);
        workItemRepository.save(saved);
        return reactivated;
    }

    /**
     * Applies complexity/effort from the mapped ADO fields, honoring manual overrides:
     * a field whose current source is MANUAL_OVERRIDE is left untouched by this sync run.
     */
    private void applyComplexityAndEffort(WorkItem workItem, Map<String, Object> fields, WorkflowConfig config,
                                           String rawType) {
        if (workItem.getComplexitySource() != FieldValueSource.MANUAL_OVERRIDE) {
            Double complexity = config.resolveFieldMapping(FieldMapping.InternalFieldTarget.COMPLEXITY, rawType)
                    .map(m -> getDouble(fields, m.getAzureFieldReferenceName()))
                    .orElse(null);
            workItem.setComplexity(complexity);
            workItem.setComplexitySource(FieldValueSource.AZURE_SYNC);
        }

        if (workItem.getEffortSource() != FieldValueSource.MANUAL_OVERRIDE) {
            Double effort = config.resolveFieldMapping(FieldMapping.InternalFieldTarget.EFFORT, rawType)
                    .map(m -> getDouble(fields, m.getAzureFieldReferenceName()))
                    .orElse(null);
            workItem.setEffort(effort);
            workItem.setEffortSource(FieldValueSource.AZURE_SYNC);
        }
    }

    /**
     * Re-fetches the given work items from Azure DevOps (batched) and updates only their
     * {@code parentExternalId} from the current {@code System.LinkTypes.Hierarchy-Reverse}
     * relation, saving each item whose parent link changed. Used by
     * {@code WorkItemParentLinkBackfillMigration} to repair documents saved before
     * WorkItemRepositoryImpl.save() persisted parentExternalId, and safe to re-run any number of
     * times — it always reflects Azure's current state and is a no-op for items whose stored
     * value already matches.
     *
     * @return the number of items whose stored parentExternalId was changed
     */
    public int backfillParentLinks(List<WorkItem> items, String orgUrl, String authHeader) {
        if (items.isEmpty()) return 0;

        Map<String, WorkItem> byExternalId = new java.util.HashMap<>();
        List<Integer> ids = new ArrayList<>();
        for (WorkItem item : items) {
            byExternalId.put(item.getExternalId(), item);
            try {
                ids.add(Integer.parseInt(item.getExternalId()));
            } catch (NumberFormatException e) {
                log.warn("backfillParentLinks: invalid externalId={} for workItemId={}",
                        item.getExternalId(), item.getId());
            }
        }
        if (ids.isEmpty()) return 0;

        List<AdoWorkItemDto> adoItems = azureDevOpsClient.getWorkItemsBatch(orgUrl, ids, authHeader);

        int updated = 0;
        for (AdoWorkItemDto adoItem : adoItems) {
            WorkItem item = byExternalId.get(String.valueOf(adoItem.id()));
            if (item == null) continue;

            String parentExternalId = extractParentExternalId(adoItem);
            if (java.util.Objects.equals(parentExternalId, item.getParentExternalId())) continue;

            item.setParentExternalId(parentExternalId);
            workItemRepository.save(item);
            updated++;
        }
        return updated;
    }

    /**
     * Rebuilds the complete transition history for a work item from the Azure DevOps updates API.
     * Clears existing transitions and rebuilds from full ADO history.
     * This method is reusable by both the initial full-sync path and ForceResyncProjectUseCase.
     */
    public void rebuildTransitionHistory(WorkItem item, WorkflowConfig config, String orgUrl, String authHeader) {
        String workItemId = item.getId();
        if (workItemId == null) return;

        WorkflowConfig effectiveConfig = config != null ? config : DefaultWorkflowConfigFactory.fallback();

        List<AdoWorkItemUpdateDto> updates = buildFullTransitionHistoryById(workItemId, item.getOrganizationId(),
                item.getExternalId(), effectiveConfig, orgUrl, authHeader, item.getRawStatus(),
                item.getCreatedAt(), item.getRawType());
        recordActivity(item, updates, orgUrl, authHeader, new ActivityCollector());

        List<WorkItemTransition> transitions = workItemTransitionRepository.findAllByWorkItemId(workItemId);
        item.setStartedAt(deriveStartedAt(transitions));
        item.setCompletedAt(deriveCompletedAt(transitions));
        workItemRepository.save(item);
    }

    /** @return the updates fetched to build the full history, or null when no fetch was needed */
    private List<AdoWorkItemUpdateDto> buildTransitions(WorkItem workItem, String storedRawStatus, AdoWorkItemDto adoItem,
                                   WorkflowConfig config, String orgUrl, String authHeader, String rawType) {
        String workItemId = workItem.getId();
        if (workItemId == null) return null;

        boolean hasTransitions = workItemTransitionRepository.existsByWorkItemId(workItemId);

        if (!hasTransitions) {
            return buildFullTransitionHistoryById(workItemId, workItem.getOrganizationId(),
                    String.valueOf(adoItem.id()), config, orgUrl, authHeader,
                    workItem.getRawStatus(), workItem.getCreatedAt(), rawType);
        }
        appendIncrementalTransition(workItemId, workItem.getOrganizationId(),
                storedRawStatus, adoItem, config, rawType);
        return null;
    }

    private List<AdoWorkItemUpdateDto> buildFullTransitionHistoryById(String workItemId, String orgId, String externalId,
                                                 WorkflowConfig config, String orgUrl, String authHeader,
                                                 String currentRawStatus, Instant createdAt, String rawType) {
        int adoItemId;
        try {
            adoItemId = Integer.parseInt(externalId);
        } catch (NumberFormatException e) {
            log.warn("rebuildTransitionHistory: invalid externalId={} for workItemId={}", externalId, workItemId);
            return null;
        }
        List<AdoWorkItemUpdateDto> updates = azureDevOpsClient.getWorkItemUpdates(orgUrl, adoItemId, authHeader);

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

            // revisedDate is ADO's "valid-until" sentinel (often 9999-01-01); use System.ChangedDate instead.
            Instant occurredAt = getChangedDateFromUpdate(update);
            if (occurredAt == null) occurredAt = Instant.now();

            WorkItem.ResolvedStatus resolved = resolveStatus(rawType, newVal, config);
            transitions.add(new WorkItemTransition(
                    null, orgId, workItemId, prevStatus, newVal, resolved,
                    occurredAt, Instant.now(), true, null, Instant.now(), Instant.now()));
            prevStatus = newVal;
        }

        if (transitions.isEmpty()) {
            WorkItem.ResolvedStatus resolved = resolveStatus(rawType, currentRawStatus, config);
            transitions.add(new WorkItemTransition(
                    null, orgId, workItemId, null, currentRawStatus, resolved,
                    createdAt != null ? createdAt : Instant.now(),
                    Instant.now(), true, null, Instant.now(), Instant.now()));
        }

        for (WorkItemTransition t : transitions) {
            workItemTransitionRepository.upsertByCompositeKey(t);
        }
        return updates;
    }

    /** Pairs (user, day) and the count of the activity stored by one sync. */
    private static final class ActivityCollector {
        final Set<ActivityDay> days = new java.util.LinkedHashSet<>();
        int stored;
    }

    /**
     * Stores the revisions of the item above the highest one already stored, so a repeat is a
     * no-op and an item with transitions but no activity (repair) is backfilled in full.
     *
     * @param prefetched the item's updates when the caller already fetched them, else null (fetched here)
     */
    private void recordActivity(WorkItem item, List<AdoWorkItemUpdateDto> prefetched, String orgUrl,
                                String authHeader, ActivityCollector collector) {
        if (item.getId() == null) return;
        int adoItemId;
        try {
            adoItemId = Integer.parseInt(item.getExternalId());
        } catch (NumberFormatException e) {
            return;
        }
        String orgId = item.getOrganizationId();
        int maxStored = workItemActivityRepository.maxRevisionByWorkItemId(orgId, item.getId()).orElse(0);
        List<AdoWorkItemUpdateDto> updates = prefetched;
        if (updates == null) {
            // A falha só de atividade não derruba o item: o próximo repair reconstrói o que faltar.
            try {
                updates = azureDevOpsClient.getWorkItemUpdates(orgUrl, adoItemId, authHeader);
            } catch (RuntimeException e) {
                log.warn("Activity skipped for work item {}: updates fetch failed ({})", item.getExternalId(),
                        e.getMessage());
                return;
            }
        }
        if (updates == null) return;

        Map<String, String> authors = new java.util.HashMap<>();
        for (AdoWorkItemUpdateDto update : updates.stream()
                .filter(u -> u.id() > maxStored).sorted(Comparator.comparingInt(AdoWorkItemUpdateDto::id)).toList()) {
            Instant changedAt = getChangedDateFromUpdate(update);
            if (changedAt == null) changedAt = parseSentinelAware(update.revisedDate());
            if (changedAt == null) {
                log.warn("Revision {} of work item {} has no change date; activity skipped", update.id(),
                        item.getExternalId());
                continue;
            }
            AdoWorkItemUpdateDto.RevisedBy by = update.revisedBy();
            String azureId = by != null ? by.id() : null;
            String uniqueName = by != null ? by.uniqueName() : null;
            String authorKey = azureId + "|" + uniqueName;
            if (!authors.containsKey(authorKey)) authors.put(authorKey, lookupUserId(orgId, azureId, uniqueName));
            String userId = authors.get(authorKey);
            LocalDate day = changedAt.atZone(ZoneOffset.UTC).toLocalDate();
            List<String> changed = update.fields() != null ? List.copyOf(update.fields().keySet()) : List.of();
            Instant now = Instant.now();
            workItemActivityRepository.upsert(new WorkItemActivity(null, orgId, item.getId(), item.getExternalId(),
                    update.id(), azureId, uniqueName, userId, changedAt, day, changed, true, null, now, now));
            collector.stored++;
            if (userId != null) collector.days.add(new ActivityDay(userId, day));
        }
    }

    private Instant parseSentinelAware(String value) {
        if (value == null) return null;
        try {
            Instant instant = Instant.parse(value);
            return instant.isAfter(ADO_SENTINEL) ? null : instant;
        } catch (Exception e) {
            return null;
        }
    }

    private void appendIncrementalTransition(String workItemId, String orgId,
                                              String storedRawStatus, AdoWorkItemDto adoItem,
                                              WorkflowConfig config, String rawType) {
        if (storedRawStatus == null) return;
        Map<String, Object> fields = adoItem.fields() != null ? adoItem.fields() : Map.of();
        String incomingStatus = getString(fields, "System.State");
        String storedStatus = storedRawStatus;

        if (incomingStatus == null || incomingStatus.equals(storedStatus)) return;

        Instant occurredAt = parseInstant(fields, "System.ChangedDate");
        if (occurredAt == null) occurredAt = Instant.now();

        WorkItem.ResolvedStatus resolved = resolveStatus(rawType, incomingStatus, config);
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

    /**
     * Resolves the status for a work item by matching both {@code workItemType} and
     * {@code rawStatus}. A {@link StatusMapping} entry whose {@code workItemType} is {@code null}
     * is a legacy record (predating per-type status mapping) and is treated as matching any
     * work item type.
     */
    private WorkItem.ResolvedStatus resolveStatus(String rawType, String rawStatus, WorkflowConfig config) {
        if (rawStatus == null || config == null || config.getStatusMappings() == null) {
            return WorkItem.ResolvedStatus.TODO;
        }
        return config.getStatusMappings().stream()
                .filter(m -> rawStatus.equalsIgnoreCase(m.getRawStatus()))
                .filter(m -> m.getWorkItemType() == null
                        || (rawType != null && rawType.equalsIgnoreCase(m.getWorkItemType())))
                .map(StatusMapping::getResolvedStatus)
                .findFirst()
                .orElse(WorkItem.ResolvedStatus.TODO);
    }

    /**
     * Resolve a sprint do item pelo iteration path, devolvendo id e nome juntos. O nome é
     * desnormalizado em {@code WorkItem.sprintName} para que a listagem do backlog possa
     * exibir e filtrar por sprint sem um join por página — e sai de graça aqui, já que
     * esta busca sempre carregou o documento inteiro e descartava tudo menos o id.
     *
     * <p>Uma iteração compartilhada por vários times existe uma vez por time (sprints são únicas por
     * {@code (organizationId, externalId, teamId)}): a busca devolve todas as "gêmeas" do projeto e
     * escolhe a canônica ({@link SharedIterations}) — a mesma que dashboards e métricas leem. A
     * busca antiga por path (resultado único) lançava {@code IncorrectResultSizeDataAccessException},
     * e o item contava como falha e ficava sem sprint.
     */
    private Sprint resolveSprint(String orgId, String projectId, String iterationPath) {
        if (iterationPath == null) return null;
        return SharedIterations.canonical(
                sprintRepository.findAllByOrganizationIdAndProjectIdAndPath(orgId, projectId, iterationPath))
                .orElse(null);
    }

    private String getString(Map<String, Object> fields, String key) {
        Object val = fields.get(key);
        return val != null ? val.toString() : null;
    }

    private Double getDouble(Map<String, Object> fields, String key) {
        if (key == null) return null;
        Object val = fields.get(key);
        if (val == null) return null;
        try { return ((Number) val).doubleValue(); } catch (Exception e) { return null; }
    }

    private static final Instant ADO_SENTINEL = Instant.parse("9998-01-01T00:00:00Z");

    private Instant parseInstant(Map<String, Object> fields, String key) {
        String val = getString(fields, key);
        if (val == null) return null;
        try {
            Instant instant = Instant.parse(val);
            // ADO uses 9999-01-01 as "not set" sentinel -- treat as null
            return instant.isAfter(ADO_SENTINEL) ? null : instant;
        } catch (Exception e) { return null; }
    }

    // Azure DevOps has no scalar "parent" field -- the parent link (if any) is expressed as a
    // System.LinkTypes.Hierarchy-Reverse relation whose url ends in the parent's work item id
    // (e.g. .../_apis/wit/workItems/1234). A work item has at most one parent, so at most one
    // such relation is ever present.
    private String extractParentExternalId(AdoWorkItemDto adoItem) {
        List<AdoWorkItemDto.AdoWorkItemRelationDto> relations = adoItem.relations();
        if (relations == null) return null;
        for (AdoWorkItemDto.AdoWorkItemRelationDto relation : relations) {
            if ("System.LinkTypes.Hierarchy-Reverse".equals(relation.rel()) && relation.url() != null) {
                int lastSlash = relation.url().lastIndexOf('/');
                if (lastSlash >= 0 && lastSlash < relation.url().length() - 1) {
                    return relation.url().substring(lastSlash + 1);
                }
            }
        }
        return null;
    }

    private static final List<String> IDENTITY_FIELDS =
            List.of("System.AssignedTo", "System.CreatedBy", "System.ChangedBy");

    private static void collectIdentities(AdoWorkItemDto adoItem,
                                          Map<String, AzureOrgUserCollector.ObservedIdentity> seen) {
        if (adoItem.fields() == null) return;
        for (String field : IDENTITY_FIELDS) {
            if (!(adoItem.fields().get(field) instanceof Map<?, ?> identity)) continue;
            Object id = identity.get("id");
            if (id == null || Boolean.TRUE.equals(identity.get("isContainer"))) continue;
            Object name = identity.get("displayName");
            Object unique = identity.get("uniqueName");
            seen.putIfAbsent(id.toString(), new AzureOrgUserCollector.ObservedIdentity(id.toString(),
                    name != null ? name.toString() : null, unique != null ? unique.toString() : null,
                    AzureOrgUserSource.WORKITEM));
        }
    }

    /** One batch per sync; a failure here must never fail the work item sync. */
    private void collectSeenIdentities(String orgId, Map<String, AzureOrgUserCollector.ObservedIdentity> seen) {
        if (seen.isEmpty()) return;
        try {
            orgUserCollector.collect(orgId, seen.values());
        } catch (Exception e) {
            log.warn("Could not register identities seen in work items: org={}: {}", orgId, e.getMessage());
        }
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

    private String getAssigneeEmail(Map<String, Object> fields) {
        Object val = fields.get("System.AssignedTo");
        if (val == null) return null;
        if (val instanceof Map<?, ?> identity) {
            Object uniqueName = identity.get("uniqueName");
            return uniqueName != null ? uniqueName.toString() : null;
        }
        return null;
    }

    private void resolveAssigneeUserId(String orgId, WorkItem workItem) {
        String externalId = workItem.getAssigneeExternalId();
        String email = workItem.getAssigneeEmail();
        // Azure is the source of truth: a reassignment to someone without an Andromeda
        // user (or no assignee) must not keep the previous user.
        workItem.setAssigneeUserId(lookupUserId(orgId, externalId, email));
    }

    /** Azure id first, then e-mail (case-insensitive); null when nobody of the organization matches. */
    private String lookupUserId(String orgId, String azureId, String email) {
        if (azureId != null) {
            Optional<andromeda.sei.ms.feature.user.domain.User> byAzureId =
                    userRepository.findByOrganizationIdAndAzureId(orgId, azureId);
            if (byAzureId.isPresent()) return byAzureId.get().getId();
        }
        if (email != null && !email.isBlank()) {
            return userRepository.findAllByOrganizationIdAndEmailIgnoringCase(orgId, email.trim()).stream()
                    .findFirst()
                    .map(andromeda.sei.ms.feature.user.domain.User::getId)
                    .orElse(null);
        }
        return null;
    }

    private Instant getChangedDateFromUpdate(AdoWorkItemUpdateDto update) {
        if (update.fields() == null) return null;
        Object changedDate = update.fields().get("System.ChangedDate");
        if (changedDate == null) return null;
        // The field value is a map with "newValue" key in the updates API
        if (changedDate instanceof Map<?, ?> m) {
            Object newVal = m.get("newValue");
            if (newVal == null) newVal = m.get("oldValue");
            if (newVal != null) {
                try { return Instant.parse(newVal.toString()); } catch (Exception ignored) {}
            }
        }
        try { return Instant.parse(changedDate.toString()); } catch (Exception ignored) {}
        return null;
    }

    @SafeVarargs
    private <T> T firstNonNull(T... values) {
        for (T v : values) { if (v != null) return v; }
        return null;
    }
}
