package andromeda.sei.ms.feature.github.infrastructure.persistence;

import andromeda.sei.ms.feature.github.domain.GithubReview;
import andromeda.sei.ms.feature.github.domain.GithubReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class GithubReviewRepositoryImpl implements GithubReviewRepository {

    private final GithubReviewMongoRepository mongoRepository;
    private final GithubReviewDocumentMapper mapper;
    private final MongoTemplate mongoTemplate;

    @Override
    public Optional<GithubReview> findByOrganizationIdAndPullRequestIdAndExternalId(
            String organizationId, String pullRequestId, String externalId) {
        return mongoRepository.findByOrganizationIdAndPullRequestIdAndExternalId(
                organizationId, pullRequestId, externalId).map(mapper::toDomain);
    }

    @Override
    public List<GithubReview> findAllByOrganizationIdAndPullRequestId(String organizationId, String pullRequestId) {
        return mongoRepository.findAllByOrganizationIdAndPullRequestId(organizationId, pullRequestId)
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    public GithubReview save(GithubReview review) {
        GithubReviewDocument doc = mapper.toDocument(review);
        Instant now = Instant.now();
        doc.setUpdatedAt(now);
        if (doc.getCreatedAt() == null) {
            doc.setCreatedAt(now);
        }

        Query query = Query.query(Criteria.where("organizationId").is(review.getOrganizationId())
                .and("pullRequestId").is(review.getPullRequestId())
                .and("externalId").is(review.getExternalId()));

        Update update = new Update()
                .set("reviewerGithubLogin", doc.getReviewerGithubLogin())
                .set("reviewerUserId", doc.getReviewerUserId())
                .set("result", doc.getResult())
                .set("submittedAt", doc.getSubmittedAt())
                .set("active", doc.isActive())
                .set("updatedAt", now)
                .setOnInsert("organizationId", doc.getOrganizationId())
                .setOnInsert("pullRequestId", doc.getPullRequestId())
                .setOnInsert("externalId", doc.getExternalId())
                .setOnInsert("createdAt", doc.getCreatedAt() != null ? doc.getCreatedAt() : now);

        mongoTemplate.upsert(query, update, GithubReviewDocument.class);

        return findByOrganizationIdAndPullRequestIdAndExternalId(
                review.getOrganizationId(), review.getPullRequestId(), review.getExternalId())
                .orElseThrow();
    }
}
