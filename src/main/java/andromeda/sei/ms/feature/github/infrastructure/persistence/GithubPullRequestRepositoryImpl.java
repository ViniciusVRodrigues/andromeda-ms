package andromeda.sei.ms.feature.github.infrastructure.persistence;

import andromeda.sei.ms.feature.github.domain.GithubPullRequest;
import andromeda.sei.ms.feature.github.domain.GithubPullRequestRepository;
import andromeda.sei.ms.feature.github.domain.PullRequestStatus;
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
public class GithubPullRequestRepositoryImpl implements GithubPullRequestRepository {

    private final GithubPullRequestMongoRepository mongoRepository;
    private final GithubPullRequestDocumentMapper mapper;
    private final MongoTemplate mongoTemplate;

    @Override
    public Optional<GithubPullRequest> findByOrganizationIdAndRepoIdAndExternalId(
            String organizationId, String repoId, String externalId) {
        return mongoRepository.findByOrganizationIdAndRepoIdAndExternalId(organizationId, repoId, externalId)
                .map(mapper::toDomain);
    }

    @Override
    public List<GithubPullRequest> findAllByOrganizationIdAndRepoId(String organizationId, String repoId) {
        return mongoRepository.findAllByOrganizationIdAndRepoId(organizationId, repoId)
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<GithubPullRequest> findAllByOrganizationIdAndAuthorUserIdAndCreatedAtBetween(
            String organizationId, String authorUserId, Instant from, Instant to) {
        return mongoRepository.findAllByOrganizationIdAndAuthorUserIdAndCreatedAtBetween(
                        organizationId, authorUserId, from, to)
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<GithubPullRequest> findAllByOrganizationIdAndRepoIdInAndStatus(
            String organizationId, List<String> repoIds, PullRequestStatus status) {
        return mongoRepository.findAllByOrganizationIdAndRepoIdInAndStatus(organizationId, repoIds, status)
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    public GithubPullRequest save(GithubPullRequest pullRequest) {
        GithubPullRequestDocument doc = mapper.toDocument(pullRequest);
        Instant now = Instant.now();
        doc.setUpdatedAt(now);

        Query query = Query.query(Criteria.where("organizationId").is(pullRequest.getOrganizationId())
                .and("repoId").is(pullRequest.getRepoId())
                .and("externalId").is(pullRequest.getExternalId()));

        Update update = new Update()
                .set("number", doc.getNumber())
                .set("title", doc.getTitle())
                .set("status", doc.getStatus())
                .set("authorGithubLogin", doc.getAuthorGithubLogin())
                .set("authorUserId", doc.getAuthorUserId())
                .set("createdAt", doc.getCreatedAt())
                .set("mergedAt", doc.getMergedAt())
                .set("closedAt", doc.getClosedAt())
                .set("externalUpdatedAt", doc.getExternalUpdatedAt())
                .set("active", doc.isActive())
                .set("updatedAt", now)
                .setOnInsert("organizationId", doc.getOrganizationId())
                .setOnInsert("repoId", doc.getRepoId())
                .setOnInsert("externalId", doc.getExternalId());

        mongoTemplate.upsert(query, update, GithubPullRequestDocument.class);

        return findByOrganizationIdAndRepoIdAndExternalId(
                pullRequest.getOrganizationId(), pullRequest.getRepoId(), pullRequest.getExternalId())
                .orElseThrow();
    }

    @Override
    public void updateMetrics(String id, Double cycleTimeHours, Double timeToFirstReviewHours) {
        Query query = Query.query(Criteria.where("_id").is(id));
        Update update = new Update()
                .set("cycleTimeHours", cycleTimeHours)
                .set("timeToFirstReviewHours", timeToFirstReviewHours)
                .set("updatedAt", Instant.now());
        mongoTemplate.updateFirst(query, update, GithubPullRequestDocument.class);
    }
}
