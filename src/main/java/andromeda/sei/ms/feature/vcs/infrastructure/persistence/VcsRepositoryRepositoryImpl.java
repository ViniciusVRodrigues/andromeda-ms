package andromeda.sei.ms.feature.vcs.infrastructure.persistence;

import andromeda.sei.ms.feature.vcs.domain.VcsProvider;
import andromeda.sei.ms.feature.vcs.domain.VcsRepository;
import andromeda.sei.ms.feature.vcs.domain.VcsRepositoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class VcsRepositoryRepositoryImpl implements VcsRepositoryRepository {

    private static final String ORGANIZATION_ID = "organizationId";
    private static final String PROVIDER = "provider";
    private static final String ACTIVE = "active";

    private final MongoTemplate mongoTemplate;
    private final VcsDocumentMapper mapper;

    @Override
    public Optional<VcsRepository> findByIdAndOrganizationId(String id, String organizationId) {
        Query query = Query.query(Criteria.where("_id").is(id).and(ORGANIZATION_ID).is(organizationId));
        return Optional.ofNullable(mongoTemplate.findOne(query, VcsRepositoryDocument.class)).map(mapper::toDomain);
    }

    @Override
    public Optional<VcsRepository> findByOrganizationIdAndProviderAndExternalRepoId(
            String organizationId, VcsProvider provider, String externalRepoId) {
        Query query = Query.query(Criteria.where(ORGANIZATION_ID).is(organizationId)
                .and(PROVIDER).is(provider)
                .and("externalRepoId").is(externalRepoId));
        return Optional.ofNullable(mongoTemplate.findOne(query, VcsRepositoryDocument.class)).map(mapper::toDomain);
    }

    @Override
    public Optional<VcsRepository> findByOrganizationIdAndProviderAndDisplayName(
            String organizationId, VcsProvider provider, String displayName) {
        Query query = Query.query(Criteria.where(ORGANIZATION_ID).is(organizationId)
                .and(PROVIDER).is(provider)
                .and("displayName").is(displayName)
                .and(ACTIVE).is(true));
        return Optional.ofNullable(mongoTemplate.findOne(query, VcsRepositoryDocument.class)).map(mapper::toDomain);
    }

    @Override
    public List<VcsRepository> findAllByOrganizationIdAndIdIn(String organizationId, Collection<String> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        Query query = Query.query(Criteria.where(ORGANIZATION_ID).is(organizationId)
                .and("_id").in(ids)
                .and(ACTIVE).is(true));
        return mongoTemplate.find(query, VcsRepositoryDocument.class).stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<VcsRepository> findAllByOrganizationIdAndProvider(String organizationId, VcsProvider provider) {
        Query query = Query.query(Criteria.where(ORGANIZATION_ID).is(organizationId)
                .and(PROVIDER).is(provider)
                .and(ACTIVE).is(true));
        return mongoTemplate.find(query, VcsRepositoryDocument.class).stream().map(mapper::toDomain).toList();
    }

    @Override
    public VcsRepository save(VcsRepository repository) {
        VcsRepositoryDocument doc = mapper.toDocument(repository);
        Instant now = Instant.now();
        doc.setUpdatedAt(now);
        if (doc.getCreatedAt() == null) doc.setCreatedAt(now);
        return mapper.toDomain(mongoTemplate.save(doc));
    }
}
