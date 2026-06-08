package andromeda.sei.ms.feature.workitem.infrastructure.persistence;

import andromeda.sei.ms.feature.workitem.domain.WorkItemTransition;
import andromeda.sei.ms.feature.workitem.domain.WorkItemTransitionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class WorkItemTransitionRepositoryImpl implements WorkItemTransitionRepository {

    private final WorkItemTransitionMongoRepository mongoRepository;
    private final WorkItemTransitionDocumentMapper mapper;
    private final MongoTemplate mongoTemplate;

    @Override
    public WorkItemTransition save(WorkItemTransition transition) {
        WorkItemTransitionDocument doc = mapper.toDocument(transition);
        Instant now = Instant.now();
        doc.setUpdatedAt(now);
        if (doc.getCreatedAt() == null) {
            doc.setCreatedAt(now);
        }
        return mapper.toDomain(mongoRepository.save(doc));
    }

    @Override
    public List<WorkItemTransition> saveAll(List<WorkItemTransition> transitions) {
        Instant now = Instant.now();
        List<WorkItemTransitionDocument> docs = transitions.stream().map(t -> {
            WorkItemTransitionDocument doc = mapper.toDocument(t);
            doc.setUpdatedAt(now);
            if (doc.getCreatedAt() == null) {
                doc.setCreatedAt(now);
            }
            return doc;
        }).toList();
        return mongoRepository.saveAll(docs).stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<WorkItemTransition> findAllByWorkItemId(String workItemId) {
        return mongoRepository.findAllByWorkItemId(workItemId).stream().map(mapper::toDomain).toList();
    }

    @Override
    public boolean existsByWorkItemId(String workItemId) {
        return mongoRepository.existsByWorkItemId(workItemId);
    }

    @Override
    public WorkItemTransition upsertByCompositeKey(WorkItemTransition transition) {
        Instant now = Instant.now();

        Query query = Query.query(Criteria.where("workItemId").is(transition.getWorkItemId())
                .and("occurredAt").is(transition.getOccurredAt())
                .and("toRawStatus").is(transition.getToRawStatus()));

        Update update = new Update()
                .set("organizationId", transition.getOrganizationId())
                .set("fromRawStatus", transition.getFromRawStatus())
                .set("toResolvedStatus", transition.getToResolvedStatus())
                .set("recordedAt", transition.getRecordedAt())
                .set("active", transition.isActive())
                .set("updatedAt", now)
                .setOnInsert("workItemId", transition.getWorkItemId())
                .setOnInsert("occurredAt", transition.getOccurredAt())
                .setOnInsert("toRawStatus", transition.getToRawStatus())
                .setOnInsert("createdAt", now);

        mongoTemplate.upsert(query, update, WorkItemTransitionDocument.class);

        return mongoTemplate.findOne(query, WorkItemTransitionDocument.class) != null
                ? mapper.toDomain(mongoTemplate.findOne(query, WorkItemTransitionDocument.class))
                : save(transition);
    }
}
