package andromeda.sei.ms.feature.integration.infrastructure.persistence;

import andromeda.sei.ms.feature.integration.domain.Integration;
import andromeda.sei.ms.feature.integration.domain.SyncOperation;
import andromeda.sei.ms.feature.integration.domain.SyncStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface IntegrationSyncLogMongoRepository extends MongoRepository<IntegrationSyncLogDocument, String> {

    List<IntegrationSyncLogDocument> findByOrganizationIdAndIntegrationOrderByStartedAtDesc(
            String organizationId, Integration integration, Pageable pageable);

    List<IntegrationSyncLogDocument> findByOrganizationIdAndIntegrationAndProjectIdOrderByStartedAtDesc(
            String organizationId, Integration integration, String projectId, Pageable pageable);

    Optional<IntegrationSyncLogDocument> findByIdAndOrganizationId(String id, String organizationId);

    List<IntegrationSyncLogDocument> findByOrganizationIdAndParentSyncIdOrderByStartedAtAsc(
            String organizationId, String parentSyncId);

    boolean existsByOrganizationIdAndProjectIdAndStatusAndOperationIn(
            String organizationId, String projectId, SyncStatus status, Collection<SyncOperation> operations);
}
