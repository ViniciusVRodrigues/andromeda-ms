package andromeda.sei.ms.feature.github.infrastructure.persistence;

import andromeda.sei.ms.feature.github.domain.GithubConnection;
import andromeda.sei.ms.feature.github.domain.GithubConnectionRepository;
import andromeda.sei.ms.feature.github.domain.GithubConnectionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class GithubConfigRepositoryImpl implements GithubConnectionRepository {

    private final GithubConfigMongoRepository mongoRepository;
    private final GithubConfigDocumentMapper mapper;

    @Override
    public Optional<GithubConnection> findByOrganizationIdAndActiveTrue(String organizationId) {
        return mongoRepository.findByOrganizationIdAndActiveTrue(organizationId).map(mapper::toDomain);
    }

    @Override
    public List<GithubConnection> findAllByStatusAndActiveTrue(GithubConnectionStatus status) {
        return mongoRepository.findAllByStatusAndActiveTrue(status)
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    public GithubConnection save(GithubConnection connection) {
        GithubConfigDocument doc = mapper.toDocument(connection);
        Instant now = Instant.now();
        doc.setUpdatedAt(now);
        if (doc.getCreatedAt() == null) {
            doc.setCreatedAt(now);
        }
        return mapper.toDomain(mongoRepository.save(doc));
    }
}
