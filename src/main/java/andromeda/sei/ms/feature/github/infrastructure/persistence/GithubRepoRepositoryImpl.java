package andromeda.sei.ms.feature.github.infrastructure.persistence;

import andromeda.sei.ms.feature.github.domain.GithubRepo;
import andromeda.sei.ms.feature.github.domain.GithubRepoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class GithubRepoRepositoryImpl implements GithubRepoRepository {

    private final GithubRepoMongoRepository mongoRepository;
    private final GithubRepoDocumentMapper mapper;

    @Override
    public Optional<GithubRepo> findByOrganizationIdAndExternalId(String organizationId, String externalId) {
        return mongoRepository.findByOrganizationIdAndExternalId(organizationId, externalId).map(mapper::toDomain);
    }

    @Override
    public Optional<GithubRepo> findByIdAndOrganizationId(String id, String organizationId) {
        return mongoRepository.findByIdAndOrganizationId(id, organizationId).map(mapper::toDomain);
    }

    @Override
    public List<GithubRepo> findAllByOrganizationId(String organizationId) {
        return mongoRepository.findAllByOrganizationIdAndActiveTrue(organizationId)
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<GithubRepo> findAllByOrganizationIdAndFullNameInAndActiveTrue(
            String organizationId, List<String> fullNames) {
        return mongoRepository.findAllByOrganizationIdAndFullNameInAndActiveTrue(organizationId, fullNames)
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    public GithubRepo save(GithubRepo repo) {
        GithubRepoDocument doc = mapper.toDocument(repo);
        Instant now = Instant.now();
        doc.setUpdatedAt(now);
        if (doc.getCreatedAt() == null) {
            doc.setCreatedAt(now);
        }
        return mapper.toDomain(mongoRepository.save(doc));
    }
}
