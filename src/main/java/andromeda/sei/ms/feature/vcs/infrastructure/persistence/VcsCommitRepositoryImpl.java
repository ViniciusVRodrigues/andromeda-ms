package andromeda.sei.ms.feature.vcs.infrastructure.persistence;

import andromeda.sei.ms.feature.vcs.domain.VcsCommit;
import andromeda.sei.ms.feature.vcs.domain.VcsCommitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

/** {@code vcs_commits}; {@link #save} upserts on {@code (organizationId, repositoryId, sha)}. */
@Repository
@RequiredArgsConstructor
public class VcsCommitRepositoryImpl implements VcsCommitRepository {

    private static final String ORGANIZATION_ID = "organizationId";
    private static final String REPOSITORY_ID = "repositoryId";

    private final MongoTemplate mongoTemplate;
    private final VcsDocumentMapper mapper;

    @Override
    public Optional<VcsCommit> findByOrganizationIdAndRepositoryIdAndSha(String organizationId, String repositoryId,
                                                                        String sha) {
        Query query = Query.query(Criteria.where(ORGANIZATION_ID).is(organizationId)
                .and(REPOSITORY_ID).is(repositoryId).and("sha").is(sha));
        return Optional.ofNullable(mongoTemplate.findOne(query, VcsCommitDocument.class)).map(mapper::toDomain);
    }

    /** Half-open {@code [from, to)} — a derived {@code Between} would exclude both ends. */
    @Override
    public long countByOrganizationIdAndRepositoryIdAndAuthoredAtBetween(String organizationId, String repositoryId,
                                                                         Instant from, Instant to) {
        Query query = Query.query(Criteria.where(ORGANIZATION_ID).is(organizationId)
                .and(REPOSITORY_ID).is(repositoryId)
                .and("authoredAt").gte(from).lt(to)
                .and("active").is(true));
        return mongoTemplate.count(query, VcsCommitDocument.class);
    }

    @Override
    public VcsCommit save(VcsCommit commit) {
        Instant now = Instant.now();
        Query query = Query.query(Criteria.where(ORGANIZATION_ID).is(commit.getOrganizationId())
                .and(REPOSITORY_ID).is(commit.getRepositoryId()).and("sha").is(commit.getSha()));
        Update update = new Update()
                .set("message", commit.getMessage())
                .set("authorLogin", commit.getAuthorLogin())
                .set("authorUserId", commit.getAuthorUserId())
                .set("authoredAt", commit.getAuthoredAt())
                .set("active", commit.isActive())
                .set("updatedAt", now)
                .setOnInsert(ORGANIZATION_ID, commit.getOrganizationId())
                .setOnInsert(REPOSITORY_ID, commit.getRepositoryId())
                .setOnInsert("provider", commit.getProvider())
                .setOnInsert("sha", commit.getSha())
                .setOnInsert("createdAt", commit.getCreatedAt() != null ? commit.getCreatedAt() : now);
        mongoTemplate.upsert(query, update, VcsCommitDocument.class);
        return findByOrganizationIdAndRepositoryIdAndSha(commit.getOrganizationId(), commit.getRepositoryId(),
                commit.getSha()).orElseThrow();
    }
}
