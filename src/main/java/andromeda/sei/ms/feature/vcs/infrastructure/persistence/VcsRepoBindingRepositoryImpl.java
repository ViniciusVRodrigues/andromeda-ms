package andromeda.sei.ms.feature.vcs.infrastructure.persistence;

import andromeda.sei.ms.feature.vcs.domain.VcsProvider;
import andromeda.sei.ms.feature.vcs.domain.VcsRepoBinding;
import andromeda.sei.ms.feature.vcs.domain.VcsRepoBindingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class VcsRepoBindingRepositoryImpl implements VcsRepoBindingRepository {

    private static final String ORGANIZATION_ID = "organizationId";
    private static final String ACTIVE = "active";

    private final MongoTemplate mongoTemplate;
    private final VcsDocumentMapper mapper;

    @Override
    public Optional<VcsRepoBinding> findByIdAndOrganizationIdAndActiveTrue(String id, String organizationId) {
        return findOne(Criteria.where("_id").is(id).and(ORGANIZATION_ID).is(organizationId).and(ACTIVE).is(true));
    }

    @Override
    public Optional<VcsRepoBinding> findActiveByOrganizationIdAndRepositoryId(String organizationId,
                                                                              String repositoryId) {
        return findOne(Criteria.where(ORGANIZATION_ID).is(organizationId)
                .and("repositoryId").is(repositoryId).and(ACTIVE).is(true));
    }

    @Override
    public List<VcsRepoBinding> findAllActiveByOrganizationId(String organizationId) {
        return find(Criteria.where(ORGANIZATION_ID).is(organizationId).and(ACTIVE).is(true));
    }

    @Override
    public List<VcsRepoBinding> findAllActiveByOrganizationIdAndProjectId(String organizationId, String projectId) {
        return find(Criteria.where(ORGANIZATION_ID).is(organizationId)
                .and("projectId").is(projectId).and(ACTIVE).is(true));
    }

    @Override
    public List<VcsRepoBinding> findAllActiveByOrganizationIdAndProvider(String organizationId, VcsProvider provider) {
        return find(Criteria.where(ORGANIZATION_ID).is(organizationId)
                .and("provider").is(provider).and(ACTIVE).is(true));
    }

    @Override
    public List<VcsRepoBinding> findAllSyncEnabledByOrganizationIdAndProvider(String organizationId,
                                                                              VcsProvider provider) {
        return find(Criteria.where(ORGANIZATION_ID).is(organizationId)
                .and("provider").is(provider).and("syncEnabled").is(true).and(ACTIVE).is(true));
    }

    @Override
    public VcsRepoBinding save(VcsRepoBinding binding) {
        return mapper.toDomain(mongoTemplate.save(prepare(binding, Instant.now())));
    }

    @Override
    public void saveAll(List<VcsRepoBinding> bindings) {
        Instant now = Instant.now();
        bindings.forEach(binding -> mongoTemplate.save(prepare(binding, now)));
    }

    private VcsRepoBindingDocument prepare(VcsRepoBinding binding, Instant now) {
        VcsRepoBindingDocument doc = mapper.toDocument(binding);
        doc.setUpdatedAt(now);
        if (doc.getCreatedAt() == null) doc.setCreatedAt(now);
        return doc;
    }

    private Optional<VcsRepoBinding> findOne(Criteria criteria) {
        return Optional.ofNullable(mongoTemplate.findOne(Query.query(criteria), VcsRepoBindingDocument.class))
                .map(mapper::toDomain);
    }

    private List<VcsRepoBinding> find(Criteria criteria) {
        return mongoTemplate.find(Query.query(criteria), VcsRepoBindingDocument.class).stream()
                .map(mapper::toDomain).toList();
    }
}
