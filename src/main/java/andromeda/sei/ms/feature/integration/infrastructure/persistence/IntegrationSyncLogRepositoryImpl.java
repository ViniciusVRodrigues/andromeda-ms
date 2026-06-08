package andromeda.sei.ms.feature.integration.infrastructure.persistence;

import andromeda.sei.ms.feature.integration.domain.Integration;
import andromeda.sei.ms.feature.integration.domain.IntegrationSyncLog;
import andromeda.sei.ms.feature.integration.domain.IntegrationSyncLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class IntegrationSyncLogRepositoryImpl implements IntegrationSyncLogRepository {

    private final IntegrationSyncLogMongoRepository mongoRepository;
    private final IntegrationSyncLogDocumentMapper mapper;

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
}
