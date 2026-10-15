package andromeda.sei.ms.feature.usage.infrastructure.persistence;

import andromeda.sei.ms.feature.usage.domain.UsageEvent;
import andromeda.sei.ms.feature.usage.domain.UsageEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

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
