package andromeda.sei.ms.feature.usage.infrastructure.persistence;

import andromeda.sei.ms.feature.usage.domain.UsageEvent;
import andromeda.sei.ms.feature.usage.domain.UsageEventRepository;
import andromeda.sei.ms.feature.usage.domain.UsageEventType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

@Repository
@RequiredArgsConstructor
public class UsageEventRepositoryImpl implements UsageEventRepository {

    private final MongoTemplate mongoTemplate;

    @Override
    public void insertAll(List<UsageEvent> events) {
        if (events == null || events.isEmpty()) return;
        List<UsageEventDocument> documents = events.stream().map(UsageEventRepositoryImpl::toDocument).toList();
        mongoTemplate.insert(documents, UsageEventDocument.class);
    }

    @Override
    public Stream<UsageEvent> streamByOrganization(String organizationId, Instant from, Instant to) {
        Query query = new Query(Criteria.where("organizationId").is(organizationId)
                .and("occurredAt").gte(from).lt(to))
                .with(Sort.by(Sort.Direction.ASC, "occurredAt"));
        return mongoTemplate.stream(query, UsageEventDocument.class).map(UsageEventRepositoryImpl::toDomain);
    }

    static UsageEvent toDomain(UsageEventDocument d) {
        return UsageEvent.restore(d.getOrganizationId(), d.getUserId(), UsageEventType.valueOf(d.getType()),
                d.getScreen(), d.getViewId(), d.getSessionId(), d.getVisibleMs(), d.getElement(), d.getLabel(),
                d.getArea(), d.getAction(), d.getValue(), d.getViewport(), d.getOrigin(), d.getStatus(), d.getCode(),
                d.getOccurredAt(), d.getCreatedAt());
    }

    private static UsageEventDocument toDocument(UsageEvent e) {
        return UsageEventDocument.builder()
                .organizationId(e.getOrganizationId())
                .userId(e.getUserId())
                .type(e.getType().name())
                .screen(e.getScreen())
                .viewId(e.getViewId())
                .sessionId(e.getSessionId())
                .visibleMs(e.getVisibleMs())
                .element(e.getElement())
                .label(e.getLabel())
                .area(e.getArea())
                .action(e.getAction())
                .value(e.getValue())
                .viewport(e.getViewport())
                .origin(e.getOrigin())
                .status(e.getStatus())
                .code(e.getCode())
                .occurredAt(e.getOccurredAt())
                .active(true)
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getCreatedAt())
                .build();
    }
}
