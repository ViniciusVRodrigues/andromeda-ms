package andromeda.sei.ms.feature.integration.infrastructure.persistence;

import andromeda.sei.ms.feature.integration.domain.Integration;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface IntegrationSyncLogMongoRepository extends MongoRepository<IntegrationSyncLogDocument, String> {

    List<IntegrationSyncLogDocument> findByOrganizationIdAndIntegrationOrderByStartedAtDesc(
            String organizationId, Integration integration, Pageable pageable);

    List<IntegrationSyncLogDocument> findByOrganizationIdAndIntegrationAndProjectIdOrderByStartedAtDesc(
            String organizationId, Integration integration, String projectId, Pageable pageable);
}
