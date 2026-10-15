package andromeda.sei.ms.feature.usage.infrastructure.persistence;

import andromeda.sei.ms.feature.usage.domain.FunnelEvent;
import andromeda.sei.ms.feature.usage.domain.FunnelEventRepository;
import andromeda.sei.ms.feature.usage.domain.FunnelFlow;
import andromeda.sei.ms.feature.usage.domain.FunnelStage;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

@Repository
@RequiredArgsConstructor
public class FunnelEventRepositoryImpl implements FunnelEventRepository {

    private final MongoTemplate mongoTemplate;

    @Override
    public void insertAll(List<FunnelEvent> events) {
        if (events == null || events.isEmpty()) return;
        List<FunnelEventDocument> documents = events.stream().map(FunnelEventRepositoryImpl::toDocument).toList();
        mongoTemplate.insert(documents, FunnelEventDocument.class);
    }

    @Override
    public long linkToAccount(String funnelId, FunnelFlow flow, String organizationId, String userId, Instant since,
                              Instant linkedAt) {
        /* userId null (also matches a missing field) is what stops a funnel from being linked twice. */
        Query query = new Query(Criteria.where("funnelId").is(funnelId)
                .and("flow").is(flow.value())
                .and("userId").is(null)
                .and("createdAt").gte(since));
        Update update = new Update()
                .set("organizationId", organizationId)
                .set("userId", userId)
                .set("linkedAt", linkedAt)
                .set("updatedAt", linkedAt);
        return mongoTemplate.updateMulti(query, update, FunnelEventDocument.class).getModifiedCount();
    }

    @Override
    public Stream<FunnelEvent> streamByOrganization(String organizationId, Instant from, Instant to) {
        Query query = new Query(Criteria.where("organizationId").is(organizationId)
                .and("occurredAt").gte(from).lt(to))
                .with(Sort.by(Sort.Direction.ASC, "occurredAt"));
        return mongoTemplate.stream(query, FunnelEventDocument.class).map(FunnelEventRepositoryImpl::toDomain);
    }

    static FunnelEventDocument toDocument(FunnelEvent e) {
        return FunnelEventDocument.builder()
                .funnelId(e.getFunnelId())
                .flow(e.getFlow().value())
                .stage(e.getStage().name())
                .step(e.getStep())
                .visibleMs(e.getVisibleMs())
                .value(e.getValue())
                .viewport(e.getViewport())
                .occurredAt(e.getOccurredAt())
                .organizationId(e.getOrganizationId())
                .userId(e.getUserId())
                .linkedAt(e.getLinkedAt())
                .active(true)
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getCreatedAt())
                .build();
    }

    static FunnelEvent toDomain(FunnelEventDocument d) {
        return FunnelEvent.restore(d.getFunnelId(), FunnelFlow.fromValue(d.getFlow()).orElse(null),
                FunnelStage.valueOf(d.getStage()), d.getStep(), d.getVisibleMs(), d.getValue(), d.getViewport(),
                d.getOccurredAt(), d.getOrganizationId(), d.getUserId(), d.getLinkedAt(), d.getCreatedAt());
    }
}
