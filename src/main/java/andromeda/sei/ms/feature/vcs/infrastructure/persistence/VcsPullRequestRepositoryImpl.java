package andromeda.sei.ms.feature.vcs.infrastructure.persistence;

import andromeda.sei.ms.feature.vcs.domain.PullRequestStatus;
import andromeda.sei.ms.feature.vcs.domain.VcsPullRequest;
import andromeda.sei.ms.feature.vcs.domain.VcsPullRequestPage;
import andromeda.sei.ms.feature.vcs.domain.VcsPullRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * {@code vcs_pull_requests}. {@link #save} is an upsert on {@code (organizationId, repositoryId,
 * externalId)} with an explicit {@code $set} whitelist: a new domain field is NOT persisted unless
 * it is added here (the trap that once silently dropped {@code linkedWorkItemExternalId}).
 */
@Repository
@RequiredArgsConstructor
public class VcsPullRequestRepositoryImpl implements VcsPullRequestRepository {

    private static final String ORGANIZATION_ID = "organizationId";
    private static final String REPOSITORY_ID = "repositoryId";
    private static final String ACTIVE = "active";
    private static final String CREATED_AT = "createdAt";
    private static final String MERGED_AT = "mergedAt";
    private static final String STATUS = "status";

    private final MongoTemplate mongoTemplate;
    private final VcsDocumentMapper mapper;

    @Override
    public Optional<VcsPullRequest> findByOrganizationIdAndRepositoryIdAndExternalId(
            String organizationId, String repositoryId, String externalId) {
        Query query = Query.query(Criteria.where(ORGANIZATION_ID).is(organizationId)
                .and(REPOSITORY_ID).is(repositoryId)
                .and("externalId").is(externalId));
        return Optional.ofNullable(mongoTemplate.findOne(query, VcsPullRequestDocument.class)).map(mapper::toDomain);
    }

    @Override
    public Optional<VcsPullRequest> findByIdAndOrganizationId(String id, String organizationId) {
        Query query = Query.query(Criteria.where("_id").is(id).and(ORGANIZATION_ID).is(organizationId));
        return Optional.ofNullable(mongoTemplate.findOne(query, VcsPullRequestDocument.class)).map(mapper::toDomain);
    }

    @Override
    public List<VcsPullRequest> findAllByOrganizationIdAndRepositoryId(String organizationId, String repositoryId) {
        return find(Criteria.where(ORGANIZATION_ID).is(organizationId).and(REPOSITORY_ID).is(repositoryId));
    }

    @Override
    public List<VcsPullRequest> findAllByOrganizationIdAndRepositoryIdInAndStatus(
            String organizationId, Collection<String> repositoryIds, PullRequestStatus status) {
        if (repositoryIds == null || repositoryIds.isEmpty()) return List.of();
        return find(Criteria.where(ORGANIZATION_ID).is(organizationId)
                .and(REPOSITORY_ID).in(repositoryIds)
                .and("status").is(status)
                .and(ACTIVE).is(true));
    }

    @Override
    public List<VcsPullRequest> findMergedByOrganizationIdAndRepositoryIdInAndMergedAtBetween(
            String organizationId, Collection<String> repositoryIds, Instant from, Instant to) {
        if (repositoryIds == null || repositoryIds.isEmpty()) return List.of();
        return find(Criteria.where(ORGANIZATION_ID).is(organizationId)
                .and(REPOSITORY_ID).in(repositoryIds)
                .and("status").is(PullRequestStatus.MERGED)
                .and("mergedAt").gte(from).lt(to)
                .and(ACTIVE).is(true));
    }

    @Override
    public List<VcsPullRequest> findAllByOrganizationIdAndRepositoryIdInAndCreatedAtBetween(
            String organizationId, Collection<String> repositoryIds, Instant from, Instant to) {
        if (repositoryIds == null || repositoryIds.isEmpty()) return List.of();
        return find(Criteria.where(ORGANIZATION_ID).is(organizationId)
                .and(REPOSITORY_ID).in(repositoryIds)
                .and(CREATED_AT).gte(from).lt(to)
                .and(ACTIVE).is(true));
    }

    /**
     * One {@code $or} branch per reason a pull request is in the window, each a full predicate so
     * each branch uses its own index ({@code {organizationId, repositoryId, createdAt}},
     * {@code {..., mergedAt}}, {@code {..., status, createdAt}} — see DashboardQueryIndexInitializer).
     */
    @Override
    public List<VcsPullRequest> findActiveTouchedSince(String organizationId, Collection<String> repositoryIds,
                                                       Instant since) {
        if (repositoryIds == null || repositoryIds.isEmpty()) return List.of();
        return find(new Criteria().orOperator(
                        inRepositories(organizationId, repositoryIds).and(CREATED_AT).gte(since),
                        inRepositories(organizationId, repositoryIds).and(MERGED_AT).gte(since),
                        inRepositories(organizationId, repositoryIds).and(STATUS).is(PullRequestStatus.OPEN)))
                .stream().filter(VcsPullRequest::isActive).toList();
    }

    private static Criteria inRepositories(String organizationId, Collection<String> repositoryIds) {
        return Criteria.where(ORGANIZATION_ID).is(organizationId).and(REPOSITORY_ID).in(repositoryIds);
    }

    @Override
    public List<VcsPullRequest> findAllByOrganizationIdAndAuthorUserIdAndCreatedAtBetween(
            String organizationId, String authorUserId, Instant from, Instant to) {
        return find(Criteria.where(ORGANIZATION_ID).is(organizationId)
                .and("authorUserId").is(authorUserId)
                .and(CREATED_AT).gte(from).lt(to));
    }

    @Override
    public VcsPullRequestPage findPageByRepository(String organizationId, String repositoryId,
                                                   PullRequestStatus status, String authorLogin,
                                                   int page, int size) {
        Criteria criteria = Criteria.where(ORGANIZATION_ID).is(organizationId)
                .and(REPOSITORY_ID).is(repositoryId)
                .and(ACTIVE).is(true);
        if (status != null) criteria = criteria.and("status").is(status);
        if (authorLogin != null && !authorLogin.isBlank()) criteria = criteria.and("authorLogin").is(authorLogin);

        // Counting before paging so the total reflects the same filter the page was cut from.
        long total = mongoTemplate.count(Query.query(criteria), VcsPullRequestDocument.class);
        Query query = Query.query(criteria)
                .with(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, CREATED_AT)));
        List<VcsPullRequest> content = mongoTemplate.find(query, VcsPullRequestDocument.class).stream()
                .map(mapper::toDomain).toList();
        return new VcsPullRequestPage(content, page, size, total);
    }

    @Override
    public VcsPullRequest save(VcsPullRequest pullRequest) {
        VcsPullRequestDocument doc = mapper.toDocument(pullRequest);
        Instant now = Instant.now();
        Query query = Query.query(Criteria.where(ORGANIZATION_ID).is(pullRequest.getOrganizationId())
                .and(REPOSITORY_ID).is(pullRequest.getRepositoryId())
                .and("externalId").is(pullRequest.getExternalId()));

        Update update = new Update()
                .set("number", doc.getNumber())
                .set("title", doc.getTitle())
                .set("status", doc.getStatus())
                .set("authorLogin", doc.getAuthorLogin())
                .set("authorExternalId", doc.getAuthorExternalId())
                .set("authorUserId", doc.getAuthorUserId())
                .set(CREATED_AT, doc.getCreatedAt())
                .set("mergedAt", doc.getMergedAt())
                .set("closedAt", doc.getClosedAt())
                .set("externalUpdatedAt", doc.getExternalUpdatedAt())
                .set("linkedWorkItemExternalId", doc.getLinkedWorkItemExternalId())
                .set("draft", doc.getDraft())
                .set("baseBranch", doc.getBaseBranch())
                .set("sourceBranch", doc.getSourceBranch())
                .set("labels", doc.getLabels())
                .set(ACTIVE, doc.isActive())
                .set("updatedAt", now)
                .setOnInsert(ORGANIZATION_ID, doc.getOrganizationId())
                .setOnInsert(REPOSITORY_ID, doc.getRepositoryId())
                .setOnInsert("provider", doc.getProvider())
                .setOnInsert("externalId", doc.getExternalId());
        // Fetched once after merge: a later sync that did not re-fetch it must not wipe it.
        if (doc.getEarliestCommitAt() != null) update.set("earliestCommitAt", doc.getEarliestCommitAt());

        mongoTemplate.upsert(query, update, VcsPullRequestDocument.class);
        return findByOrganizationIdAndRepositoryIdAndExternalId(
                pullRequest.getOrganizationId(), pullRequest.getRepositoryId(), pullRequest.getExternalId())
                .orElseThrow();
    }

    @Override
    public void updateMetrics(String organizationId, String id, Double cycleTimeHours,
                              Double timeToFirstReviewHours, Double timeToFirstApprovalHours) {
        Query query = Query.query(Criteria.where("_id").is(id).and(ORGANIZATION_ID).is(organizationId));
        Update update = new Update()
                .set("cycleTimeHours", cycleTimeHours)
                .set("timeToFirstReviewHours", timeToFirstReviewHours)
                .set("timeToFirstApprovalHours", timeToFirstApprovalHours)
                .set("updatedAt", Instant.now());
        mongoTemplate.updateFirst(query, update, VcsPullRequestDocument.class);
    }

    private List<VcsPullRequest> find(Criteria criteria) {
        return mongoTemplate.find(Query.query(criteria), VcsPullRequestDocument.class).stream()
                .map(mapper::toDomain).toList();
    }

    /** Raw projection read (no document mapping): created or merged since {@code since}, or open. */
    @Override
    public List<VcsPullRequest> findMetricFieldsTouchedSince(String organizationId, Collection<String> repositoryIds,
                                                             Instant since) {
        if (repositoryIds == null || repositoryIds.isEmpty()) return List.of();
        java.util.Date from = java.util.Date.from(since);
        List<String> repositories = List.copyOf(repositoryIds);
        org.bson.Document filter = new org.bson.Document("$or", List.of(
                inRepositoriesRaw(organizationId, repositories).append(CREATED_AT, new org.bson.Document("$gte", from)),
                inRepositoriesRaw(organizationId, repositories).append(MERGED_AT, new org.bson.Document("$gte", from)),
                inRepositoriesRaw(organizationId, repositories).append(STATUS, PullRequestStatus.OPEN.name())));
        org.bson.Document projection = new org.bson.Document(REPOSITORY_ID, 1).append("provider", 1).append(STATUS, 1)
                .append("title", 1).append("authorLogin", 1).append("authorExternalId", 1).append("authorUserId", 1)
                .append(CREATED_AT, 1).append(MERGED_AT, 1).append("closedAt", 1).append("cycleTimeHours", 1)
                .append("timeToFirstReviewHours", 1).append("linkedWorkItemExternalId", 1).append("draft", 1)
                .append("baseBranch", 1).append("sourceBranch", 1).append("earliestCommitAt", 1).append(ACTIVE, 1);
        List<VcsPullRequest> pullRequests = new java.util.ArrayList<>();
        for (org.bson.Document d : mongoTemplate.getCollection(mongoTemplate.getCollectionName(VcsPullRequestDocument.class))
                .find(filter).projection(projection)) {
            if (!Boolean.TRUE.equals(d.getBoolean(ACTIVE, true))) continue;
            pullRequests.add(leanPullRequest(d, organizationId));
        }
        return pullRequests;
    }

    private static org.bson.Document inRepositoriesRaw(String organizationId, List<String> repositoryIds) {
        return new org.bson.Document(ORGANIZATION_ID, organizationId)
                .append(REPOSITORY_ID, new org.bson.Document("$in", repositoryIds));
    }

    static VcsPullRequest leanPullRequest(org.bson.Document d, String organizationId) {
        VcsPullRequest pr = new VcsPullRequest(d.get("_id").toString(), organizationId, d.getString(REPOSITORY_ID),
                enumOf(andromeda.sei.ms.feature.vcs.domain.VcsProvider.class, d.getString("provider")), null, null,
                d.getString("title"), enumOf(PullRequestStatus.class, d.getString(STATUS)));
        pr.setAuthorLogin(d.getString("authorLogin"));
        pr.setAuthorExternalId(d.getString("authorExternalId"));
        pr.setAuthorUserId(d.getString("authorUserId"));
        pr.setCreatedAt(instant(d.get(CREATED_AT)));
        pr.setMergedAt(instant(d.get(MERGED_AT)));
        pr.setClosedAt(instant(d.get("closedAt")));
        pr.setCycleTimeHours(number(d.get("cycleTimeHours")));
        pr.setTimeToFirstReviewHours(number(d.get("timeToFirstReviewHours")));
        pr.setLinkedWorkItemExternalId(d.getString("linkedWorkItemExternalId"));
        pr.setDraft(d.getBoolean("draft"));
        pr.setBaseBranch(d.getString("baseBranch"));
        pr.setSourceBranch(d.getString("sourceBranch"));
        pr.setEarliestCommitAt(instant(d.get("earliestCommitAt")));
        pr.setActive(true);
        return pr;
    }

    private static <E extends Enum<E>> E enumOf(Class<E> type, String name) {
        if (name == null) return null;
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Instant instant(Object value) {
        return value instanceof java.util.Date date ? date.toInstant() : null;
    }

    private static Double number(Object value) {
        return value instanceof Number n ? n.doubleValue() : null;
    }
}
