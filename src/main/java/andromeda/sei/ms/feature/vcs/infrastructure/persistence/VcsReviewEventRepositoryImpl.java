package andromeda.sei.ms.feature.vcs.infrastructure.persistence;

import andromeda.sei.ms.feature.vcs.domain.VcsReviewEvent;
import andromeda.sei.ms.feature.vcs.domain.VcsReviewEventRepository;
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

/** {@code vcs_review_events}; {@link #save} upserts on {@code (organizationId, pullRequestId, externalEventId)}. */
@Repository
@RequiredArgsConstructor
public class VcsReviewEventRepositoryImpl implements VcsReviewEventRepository {

    private static final String ORGANIZATION_ID = "organizationId";
    private static final String PULL_REQUEST_ID = "pullRequestId";
    private static final String EXTERNAL_EVENT_ID = "externalEventId";

    private final MongoTemplate mongoTemplate;
    private final VcsDocumentMapper mapper;

    @Override
    public Optional<VcsReviewEvent> findByOrganizationIdAndPullRequestIdAndExternalEventId(
            String organizationId, String pullRequestId, String externalEventId) {
        Query query = Query.query(Criteria.where(ORGANIZATION_ID).is(organizationId)
                .and(PULL_REQUEST_ID).is(pullRequestId).and(EXTERNAL_EVENT_ID).is(externalEventId));
        return Optional.ofNullable(mongoTemplate.findOne(query, VcsReviewEventDocument.class)).map(mapper::toDomain);
    }

    @Override
    public List<VcsReviewEvent> findAllByOrganizationIdAndPullRequestIdIn(String organizationId,
                                                                          Collection<String> pullRequestIds) {
        if (pullRequestIds == null || pullRequestIds.isEmpty()) return List.of();
        Query query = Query.query(Criteria.where(ORGANIZATION_ID).is(organizationId)
                .and(PULL_REQUEST_ID).in(pullRequestIds)
                .and("active").is(true));
        return mongoTemplate.find(query, VcsReviewEventDocument.class).stream().map(mapper::toDomain).toList();
    }

    @Override
    public VcsReviewEvent save(VcsReviewEvent event) {
        Instant now = Instant.now();
        Query query = Query.query(Criteria.where(ORGANIZATION_ID).is(event.getOrganizationId())
                .and(PULL_REQUEST_ID).is(event.getPullRequestId())
                .and(EXTERNAL_EVENT_ID).is(event.getExternalEventId()));
        Update update = new Update()
                .set("actorExternalId", event.getActorExternalId())
                .set("actorLogin", event.getActorLogin())
                .set("actorUserId", event.getActorUserId())
                .set("isBot", event.isBot())
                .set("kind", event.getKind())
                .set("occurredAt", event.getOccurredAt())
                .set("deleted", event.isDeleted())
                .set("active", event.isActive())
                .set("updatedAt", now)
                .setOnInsert(ORGANIZATION_ID, event.getOrganizationId())
                .setOnInsert(PULL_REQUEST_ID, event.getPullRequestId())
                .setOnInsert("repositoryId", event.getRepositoryId())
                .setOnInsert("provider", event.getProvider())
                .setOnInsert(EXTERNAL_EVENT_ID, event.getExternalEventId())
                .setOnInsert("createdAt", event.getCreatedAt() != null ? event.getCreatedAt() : now);
        mongoTemplate.upsert(query, update, VcsReviewEventDocument.class);
        return findByOrganizationIdAndPullRequestIdAndExternalEventId(event.getOrganizationId(),
                event.getPullRequestId(), event.getExternalEventId()).orElseThrow();
    }
}
