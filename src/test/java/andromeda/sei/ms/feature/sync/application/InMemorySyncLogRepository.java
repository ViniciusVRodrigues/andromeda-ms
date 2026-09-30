package andromeda.sei.ms.feature.sync.application;

import andromeda.sei.ms.feature.integration.domain.Integration;
import andromeda.sei.ms.feature.integration.domain.IntegrationSyncLog;
import andromeda.sei.ms.feature.integration.domain.IntegrationSyncLogRepository;
import andromeda.sei.ms.feature.integration.domain.SyncOperation;
import andromeda.sei.ms.feature.integration.domain.SyncStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/** Fake em memoria do log de sync, para seguir o ciclo pai/filho sem Mongo. */
class InMemorySyncLogRepository implements IntegrationSyncLogRepository {

    private final Map<String, IntegrationSyncLog> logs = new LinkedHashMap<>();
    private final AtomicInteger sequence = new AtomicInteger();

    @Override
    public IntegrationSyncLog save(IntegrationSyncLog log) {
        if (log.getId() == null) {
            log.setId("log-" + sequence.incrementAndGet());
        }
        logs.put(log.getId(), log);
        return log;
    }

    @Override
    public List<IntegrationSyncLog> findByOrganizationIdAndIntegration(String organizationId, Integration integration,
                                                                      String projectId, int limit) {
        return logs.values().stream()
                .filter(l -> organizationId.equals(l.getOrganizationId()) && l.getIntegration() == integration)
                .limit(limit)
                .toList();
    }

    @Override
    public long deleteByOrganizationIdAndIntegration(String organizationId, Integration integration) {
        return 0;
    }

    @Override
    public Optional<IntegrationSyncLog> findByIdAndOrganizationId(String id, String organizationId) {
        return Optional.ofNullable(logs.get(id)).filter(l -> organizationId.equals(l.getOrganizationId()));
    }

    @Override
    public List<IntegrationSyncLog> findAllByOrganizationIdAndParentSyncId(String organizationId, String parentSyncId) {
        return logs.values().stream()
                .filter(l -> organizationId.equals(l.getOrganizationId()))
                .filter(l -> Objects.equals(parentSyncId, l.getParentSyncId()))
                .toList();
    }

    @Override
    public boolean existsRunningByOrganizationIdAndProjectIdAndOperationIn(String organizationId, String projectId,
                                                                           Collection<SyncOperation> operations) {
        return logs.values().stream().anyMatch(l -> organizationId.equals(l.getOrganizationId())
                && Objects.equals(projectId, l.getProjectId())
                && l.getStatus() == SyncStatus.RUNNING
                && operations.contains(l.getOperation()));
    }

    @Override
    public long failRunningStartedBefore(Instant startedBefore, String errorCode, String errorMessage) {
        return 0;
    }

    List<IntegrationSyncLog> all() {
        return new ArrayList<>(logs.values());
    }

    IntegrationSyncLog get(String id) {
        return logs.get(id);
    }
}
