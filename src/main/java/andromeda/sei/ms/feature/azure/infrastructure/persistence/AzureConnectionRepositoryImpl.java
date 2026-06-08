package andromeda.sei.ms.feature.azure.infrastructure.persistence;

import andromeda.sei.ms.feature.azure.domain.AzureConnection;
import andromeda.sei.ms.feature.azure.domain.AzureConnectionRepository;
import andromeda.sei.ms.feature.azure.domain.AzureConnectionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class AzureConnectionRepositoryImpl implements AzureConnectionRepository {

    private final AzureConnectionMongoRepository mongoRepository;
    private final AzureConnectionDocumentMapper mapper;

    @Override
    public Optional<AzureConnection> findByOrganizationIdAndActiveTrue(String organizationId) {
        return mongoRepository.findByOrganizationIdAndActiveTrue(organizationId).map(mapper::toDomain);
    }

    @Override
    public List<AzureConnection> findAllByStatusAndActiveTrue(AzureConnectionStatus status) {
        return mongoRepository.findAllByStatusAndActiveTrue(status)
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    public AzureConnection save(AzureConnection connection) {
        AzureConnectionDocument doc = mapper.toDocument(connection);
        doc.setUpdatedAt(Instant.now());
        if (doc.getCreatedAt() == null) {
            doc.setCreatedAt(doc.getUpdatedAt());
        }
        return mapper.toDomain(mongoRepository.save(doc));
    }
}
