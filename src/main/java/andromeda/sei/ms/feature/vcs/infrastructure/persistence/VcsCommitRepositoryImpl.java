package andromeda.sei.ms.feature.vcs.infrastructure.persistence;

import andromeda.sei.ms.feature.vcs.domain.VcsCommit;
import andromeda.sei.ms.feature.vcs.domain.VcsProvider;
import andromeda.sei.ms.feature.vcs.domain.VcsCommitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
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

    /** Raw read (no document mapping) of the fields a commit classification reads. */
    @Override
    public List<VcsCommit> findMessagesByOrganizationIdAndRepositoryIdInAndAuthoredAtBetween(
            String organizationId, Collection<String> repositoryIds, Instant from, Instant to) {
        if (repositoryIds == null || repositoryIds.isEmpty()) return List.of();
        org.bson.Document filter = new org.bson.Document(ORGANIZATION_ID, organizationId)
                .append(REPOSITORY_ID, new org.bson.Document("$in", List.copyOf(repositoryIds)))
                .append("authoredAt", new org.bson.Document("$gte", java.util.Date.from(from)).append("$lt", java.util.Date.from(to)))
                .append("active", true);
        org.bson.Document projection = new org.bson.Document(REPOSITORY_ID, 1).append("message", 1)
                .append("authorUserId", 1).append("authoredAt", 1).append("provider", 1).append("sha", 1);
        List<VcsCommit> commits = new java.util.ArrayList<>();
        for (org.bson.Document d : mongoTemplate.getCollection(VcsCommitDocument.COLLECTION).find(filter).projection(projection)) {
            VcsCommit commit = new VcsCommit(d.get("_id").toString(), organizationId, d.getString(REPOSITORY_ID),
                    d.getString("provider") == null ? null : VcsProvider.valueOf(d.getString("provider")), d.getString("sha"));
            commit.setMessage(d.getString("message"));
            commit.setAuthorUserId(d.getString("authorUserId"));
            commit.setAuthoredAt(d.getDate("authoredAt") == null ? null : d.getDate("authoredAt").toInstant());
            commit.setActive(true);
            commits.add(commit);
        }
        return commits;
    }

    @Override
    public List<VcsCommit> findAllByOrganizationIdAndRepositoryIdInAndAuthoredAtBetween(
            String organizationId, Collection<String> repositoryIds, Instant from, Instant to) {
        if (repositoryIds == null || repositoryIds.isEmpty()) return List.of();
        Query query = Query.query(Criteria.where(ORGANIZATION_ID).is(organizationId)
                .and(REPOSITORY_ID).in(repositoryIds)
                .and("authoredAt").gte(from).lt(to)
                .and("active").is(true));
        return mongoTemplate.find(query, VcsCommitDocument.class).stream().map(mapper::toDomain).toList();
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

    /** Grouped in the database: one row per UTC day instead of every commit. */
    @Override
    public java.util.Map<java.time.LocalDate, Long> countByDay(String organizationId, Collection<String> repositoryIds,
                                                              Instant from, Instant to) {
        if (repositoryIds == null || repositoryIds.isEmpty()) return java.util.Map.of();
        org.bson.Document match = new org.bson.Document(ORGANIZATION_ID, organizationId)
                .append(REPOSITORY_ID, new org.bson.Document("$in", List.copyOf(repositoryIds)))
                .append("authoredAt", new org.bson.Document("$gte", java.util.Date.from(from))
                        .append("$lt", java.util.Date.from(to)))
                .append("active", true);
        org.bson.Document day = new org.bson.Document("$dateToString", new org.bson.Document("format", DAY_FORMAT)
                .append("date", "$authoredAt").append("timezone", "UTC"));
        List<org.bson.Document> pipeline = List.of(new org.bson.Document("$match", match),
                new org.bson.Document("$group", new org.bson.Document("_id", day)
                        .append("n", new org.bson.Document("$sum", 1))));
        java.util.Map<java.time.LocalDate, Long> counts = new java.util.HashMap<>();
        for (org.bson.Document row : mongoTemplate.getCollection(VcsCommitDocument.COLLECTION).aggregate(pipeline)) {
            counts.put(java.time.LocalDate.parse(row.getString("_id")), ((Number) row.get("n")).longValue());
        }
        return counts;
    }

    static final String DAY_FORMAT = "%Y-%m-%d";
}
