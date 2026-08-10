package andromeda.sei.ms.feature.github.infrastructure.persistence;

import andromeda.sei.ms.feature.github.domain.GithubCommit;
import andromeda.sei.ms.feature.github.domain.GithubCommitRepository;
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
public class GithubCommitRepositoryImpl implements GithubCommitRepository {

    private final GithubCommitMongoRepository mongoRepository;
    private final GithubCommitDocumentMapper mapper;
    private final MongoTemplate mongoTemplate;

    @Override
    public Optional<GithubCommit> findByOrganizationIdAndRepoIdAndSha(
            String organizationId, String repoId, String sha) {
        return mongoRepository.findByOrganizationIdAndRepoIdAndSha(organizationId, repoId, sha)
                .map(mapper::toDomain);
    }

    @Override
    public List<GithubCommit> findAllByOrganizationIdAndRepoId(String organizationId, String repoId) {
        return mongoRepository.findAllByOrganizationIdAndRepoId(organizationId, repoId)
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    public long countByOrganizationIdAndRepoIdAndAuthoredAtBetween(
            String organizationId, String repoId, Instant periodStart, Instant periodEnd) {
        return mongoRepository.countByOrganizationIdAndRepoIdAndAuthoredAtBetween(
                organizationId, repoId, periodStart, periodEnd);
    }

    @Override
    public GithubCommit save(GithubCommit commit) {
        GithubCommitDocument doc = mapper.toDocument(commit);
        Instant now = Instant.now();
        doc.setUpdatedAt(now);
        if (doc.getCreatedAt() == null) {
            doc.setCreatedAt(now);
        }

        Query query = Query.query(Criteria.where("organizationId").is(commit.getOrganizationId())
                .and("repoId").is(commit.getRepoId())
                .and("sha").is(commit.getSha()));

        Update update = new Update()
                .set("message", doc.getMessage())
                .set("authorGithubLogin", doc.getAuthorGithubLogin())
                .set("authorUserId", doc.getAuthorUserId())
                .set("authoredAt", doc.getAuthoredAt())
                .set("active", doc.isActive())
                .set("updatedAt", now)
                .setOnInsert("organizationId", doc.getOrganizationId())
                .setOnInsert("repoId", doc.getRepoId())
                .setOnInsert("sha", doc.getSha())
                .setOnInsert("createdAt", doc.getCreatedAt() != null ? doc.getCreatedAt() : now);

        mongoTemplate.upsert(query, update, GithubCommitDocument.class);

        return findByOrganizationIdAndRepoIdAndSha(commit.getOrganizationId(), commit.getRepoId(), commit.getSha())
                .orElseThrow();
    }
}
