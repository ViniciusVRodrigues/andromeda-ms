package andromeda.sei.ms.feature.workitem.infrastructure.migration;

import andromeda.sei.ms.feature.azure.application.usecase.SyncAzureWorkItemsUseCase;
import andromeda.sei.ms.feature.azure.domain.AzureAuthMethod;
import andromeda.sei.ms.feature.azure.domain.AzureConnection;
import andromeda.sei.ms.feature.azure.domain.AzureConnectionRepository;
import andromeda.sei.ms.feature.azure.domain.AzureConnectionStatus;
import andromeda.sei.ms.feature.azure.domain.AzureProjectBinding;
import andromeda.sei.ms.feature.azure.domain.AzureProjectBindingRepository;
import andromeda.sei.ms.feature.azure.infrastructure.client.AzureAuthStrategyResolver;
import andromeda.sei.ms.feature.azure.infrastructure.security.AzureTokenRefreshService;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.domain.WorkItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * One-time migration: repairs {@code work_items.parentExternalId} for documents saved before
 * {@code WorkItemRepositoryImpl.save()} persisted that field (it was computed correctly by
 * SyncAzureWorkItemsUseCase and then silently dropped on every upsert — see the fix to that
 * method's Update builder). Without this backfill, existing synced data has no parent links at
 * all and the backlog tree can never populate until the next full re-sync from Azure.
 *
 * <p>Idempotent by design: for each active Azure connection/binding (the same enumeration
 * the old scheduled Azure sync job used, now {@code AzureBindingSyncRunner}, so this never touches an org outside its own tenant), it re-fetches from Azure
 * only the work items in that project that currently have no {@code parentExternalId} stored, and
 * writes back only when Azure's current parent link differs from what's stored. Running it again
 * after a partial failure, or after the next scheduled sync has already populated some items, is
 * always safe — it just finds fewer (or zero) pending documents.
 *
 * <p>Runs after {@link WorkItemEffortBackfillMigration} (order 20); no ordering dependency
 * between them, this just keeps work_items migrations grouped together in startup logs.
 */
@Slf4j
@Component
@Order(21)
@RequiredArgsConstructor
public class WorkItemParentLinkBackfillMigration implements ApplicationRunner {

    private final AzureConnectionRepository azureConnectionRepository;
    private final AzureProjectBindingRepository azureProjectBindingRepository;
    private final AzureTokenRefreshService azureTokenRefreshService;
    private final AzureAuthStrategyResolver authStrategyResolver;
    private final WorkItemRepository workItemRepository;
    private final SyncAzureWorkItemsUseCase syncAzureWorkItemsUseCase;

    @Override
    public void run(ApplicationArguments args) {
        List<AzureConnection> connections = azureConnectionRepository
                .findAllByStatusAndActiveTrue(AzureConnectionStatus.CONNECTED);

        if (connections.isEmpty()) {
            log.info("WorkItemParentLinkBackfillMigration: no active Azure connections — nothing to migrate");
            return;
        }

        int totalUpdated = 0;
        for (AzureConnection connection : connections) {
            totalUpdated += migrateOrganization(connection);
        }

        log.info("WorkItemParentLinkBackfillMigration: migration complete, {} work_items updated", totalUpdated);
    }

    private int migrateOrganization(AzureConnection connection) {
        String orgId = connection.getOrganizationId();

        AzureConnection refreshed;
        if (connection.getAuthMethod() == AzureAuthMethod.PAT) {
            refreshed = connection;
        } else {
            try {
                refreshed = azureTokenRefreshService.refreshIfNeeded(connection);
            } catch (Exception e) {
                log.error("WorkItemParentLinkBackfillMigration: token refresh failed for org={}", orgId, e);
                return 0;
            }
        }

        String orgUrl = refreshed.getAzureOrgUrl();
        String authHeader;
        try {
            authHeader = authStrategyResolver.resolve(refreshed);
        } catch (Exception e) {
            log.error("WorkItemParentLinkBackfillMigration: auth resolution failed for org={}", orgId, e);
            return 0;
        }

        List<AzureProjectBinding> bindings = azureProjectBindingRepository
                .findAllByOrganizationIdAndActiveTrue(orgId);

        int orgUpdated = 0;
        for (AzureProjectBinding binding : bindings) {
            String projectId = binding.getProjectId();
            try {
                List<WorkItem> pending = workItemRepository
                        .findAllByOrganizationIdAndProjectId(orgId, projectId)
                        .stream()
                        .filter(WorkItem::isActive)
                        .filter(item -> item.getParentExternalId() == null)
                        .toList();

                if (pending.isEmpty()) continue;

                int updated = syncAzureWorkItemsUseCase.backfillParentLinks(pending, orgUrl, authHeader);
                orgUpdated += updated;
                log.info("WorkItemParentLinkBackfillMigration: org={} project={} candidates={} updated={}",
                        orgId, projectId, pending.size(), updated);
            } catch (Exception e) {
                log.error("WorkItemParentLinkBackfillMigration: failed for org={} project={}", orgId, projectId, e);
            }
        }
        return orgUpdated;
    }
}
