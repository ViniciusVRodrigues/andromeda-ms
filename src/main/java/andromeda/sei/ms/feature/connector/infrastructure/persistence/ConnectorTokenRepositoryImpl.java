package andromeda.sei.ms.feature.connector.infrastructure.persistence;

import andromeda.sei.ms.feature.connector.domain.ConnectorToken;
import andromeda.sei.ms.feature.connector.domain.ConnectorTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ConnectorTokenRepositoryImpl implements ConnectorTokenRepository {

    private final ConnectorTokenMongoRepository mongoRepository;
    private final ConnectorTokenDocumentMapper mapper;

    @Override
    public Optional<ConnectorToken> findByIdAndOrganizationId(String id, String organizationId) {
        return mongoRepository.findByIdAndOrganizationId(id, organizationId)
                .map(mapper::toDomain);
    }

    @Override
    public Optional<ConnectorToken> findByOrganizationIdAndTokenHash(String organizationId, String tokenHash) {
        return mongoRepository.findByOrganizationIdAndTokenHash(organizationId, tokenHash)
                .map(mapper::toDomain);
    }

    @Override
    public List<ConnectorToken> findActiveByOrganizationIdAndUserId(String organizationId, String userId) {
        return mongoRepository.findAllByOrganizationIdAndUserIdAndActiveTrue(organizationId, userId)
                .stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public ConnectorToken save(ConnectorToken token) {
        ConnectorTokenDocument doc = mapper.toDocument(token);
        doc.setUpdatedAt(Instant.now());
        return mapper.toDomain(mongoRepository.save(doc));
    }
}
