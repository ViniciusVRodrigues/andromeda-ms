package andromeda.sei.ms.feature.connector.infrastructure.persistence;

import andromeda.sei.ms.feature.connector.application.port.FluencyScoreRepository;
import andromeda.sei.ms.feature.connector.domain.FluencyScore;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.FindAndReplaceOptions;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class FluencyScoreRepositoryImpl implements FluencyScoreRepository {

    private final MongoTemplate mongoTemplate;
    private final FluencyScoreDocumentMapper mapper;

    @Override
    public void upsertByUserAndOrg(FluencyScore score) {
        FluencyScoreDocument doc = mapper.toDocument(score);
        Query query = Query.query(
                Criteria.where("organizationId").is(score.getOrganizationId())
                        .and("userId").is(score.getUserId())
        );
        mongoTemplate.findAndReplace(query, doc,
                FindAndReplaceOptions.options().upsert().returnNew());
    }

    @Override
    public Optional<FluencyScore> findByOrganizationIdAndUserId(String organizationId, String userId) {
        Query query = Query.query(
                Criteria.where("organizationId").is(organizationId)
                        .and("userId").is(userId)
                        .and("active").is(true)
        );
        FluencyScoreDocument doc = mongoTemplate.findOne(query, FluencyScoreDocument.class);
        return Optional.ofNullable(doc).map(mapper::toDomain);
    }

    @Override
    public List<FluencyScore> findAllByOrganizationId(String organizationId) {
        Query query = Query.query(Criteria.where("organizationId").is(organizationId).and("active").is(true));
        return mongoTemplate.find(query, FluencyScoreDocument.class).stream().map(mapper::toDomain).toList();
    }
}
