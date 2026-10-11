package andromeda.sei.ms.feature.integration.infrastructure.persistence;

import andromeda.sei.ms.feature.integration.domain.Integration;
import andromeda.sei.ms.feature.integration.domain.IntegrationSyncLog;
import andromeda.sei.ms.feature.integration.domain.IntegrationSyncLogRepository;
import andromeda.sei.ms.feature.integration.domain.SyncOperation;
import andromeda.sei.ms.feature.integration.domain.SyncStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class IntegrationSyncLogRepositoryImpl implements IntegrationSyncLogRepository {

    private final IntegrationSyncLogMongoRepository mongoRepository;
    private final IntegrationSyncLogDocumentMapper mapper;
    private final org.springframework.data.mongodb.core.MongoTemplate mongoTemplate;

    @Override
    public IntegrationSyncLog save(IntegrationSyncLog log) {
        IntegrationSyncLogDocument doc = mapper.toDocument(log);
        return mapper.toDomain(mongoRepository.save(doc));
    }

    @Override
    public List<IntegrationSyncLog> findByOrganizationIdAndIntegration(
            String organizationId, Integration integration, String projectId, int limit) {
        var pageable = PageRequest.of(0, limit);
        if (projectId != null) {
            return mongoRepository
                    .findByOrganizationIdAndIntegrationAndProjectIdOrderByStartedAtDesc(
                            organizationId, integration, projectId, pageable)
                    .stream().map(mapper::toDomain).toList();
        }
        return mongoRepository
                .findByOrganizationIdAndIntegrationOrderByStartedAtDesc(organizationId, integration, pageable)
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    public long deleteByOrganizationIdAndIntegration(String organizationId, Integration integration) {
        var query = org.springframework.data.mongodb.core.query.Query.query(
                org.springframework.data.mongodb.core.query.Criteria.where("organizationId").is(organizationId)
                        .and("integration").is(integration));
        return mongoTemplate.remove(query, "integration_sync_logs").getDeletedCount();
    }

    @Override
    public Optional<IntegrationSyncLog> findByIdAndOrganizationId(String id, String organizationId) {
        return mongoRepository.findByIdAndOrganizationId(id, organizationId).map(mapper::toDomain);
    }

    @Override
    public List<IntegrationSyncLog> findByOrganizationIdAndIntegrationAndOperationInAndStartedAtFrom(
            String organizationId, Integration integration, Collection<SyncOperation> operations,
            Instant startedFrom) {
        Query query = Query.query(Criteria.where("organizationId").is(organizationId)
                        .and("integration").is(integration)
                        .and("startedAt").gte(startedFrom)
                        .and("operation").in(operations))
                .with(Sort.by(Sort.Direction.DESC, "startedAt"));
        return mongoTemplate.find(query, IntegrationSyncLogDocument.class).stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<IntegrationSyncLog> findAllByOrganizationIdAndParentSyncId(String organizationId, String parentSyncId) {
        return mongoRepository.findByOrganizationIdAndParentSyncIdOrderByStartedAtAsc(organizationId, parentSyncId)
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    public boolean existsRunningByOrganizationIdAndProjectIdAndOperationIn(
            String organizationId, String projectId, Collection<SyncOperation> operations) {
        return mongoRepository.existsByOrganizationIdAndProjectIdAndStatusAndOperationIn(
                organizationId, projectId, SyncStatus.RUNNING, operations);
    }

    @Override
    public boolean existsRunningByOrganizationIdAndProjectIdInAndOperationIn(
            String organizationId, Collection<String> projectIds, Collection<SyncOperation> operations) {
        Criteria criteria = Criteria.where("organizationId").is(organizationId)
                .and("status").is(SyncStatus.RUNNING)
                .and("operation").in(operations);
        if (projectIds != null) criteria = criteria.and("projectId").in(projectIds);
        return mongoTemplate.exists(Query.query(criteria), IntegrationSyncLogDocument.class);
    }

    /**
     * Manutencao global de startup (sem tenant por construcao): so encerra logs que ficaram presos
     * em {@code RUNNING}; nao le nem expoe dados de nenhuma organizacao.
     */
    @Override
    public long failRunningStartedBefore(Instant startedBefore, String errorCode, String errorMessage) {
        Query query = Query.query(Criteria.where("status").is(SyncStatus.RUNNING)
                .and("startedAt").lt(startedBefore));
        Update update = new Update()
                .set("status", SyncStatus.FAILED)
                .set("finishedAt", Instant.now())
                .set("errorCode", errorCode)
                .set("errorMessage", errorMessage);
        return mongoTemplate.updateMulti(query, update, IntegrationSyncLogDocument.class).getModifiedCount();
    }
}
