package andromeda.sei.ms.feature.workitem.infrastructure.persistence;

import andromeda.sei.ms.feature.workitem.domain.WorkItemActivity;
import andromeda.sei.ms.feature.workitem.domain.WorkItemActivityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.OptionalInt;

@Repository
@RequiredArgsConstructor
public class WorkItemActivityRepositoryImpl implements WorkItemActivityRepository {

    private final WorkItemActivityMongoRepository mongoRepository;
    private final WorkItemActivityDocumentMapper mapper;
    private final MongoTemplate mongoTemplate;

    @Override
    public WorkItemActivity upsert(WorkItemActivity activity) {
        Instant now = Instant.now();
        Query query = Query.query(Criteria.where("organizationId").is(activity.getOrganizationId())
                .and("workItemId").is(activity.getWorkItemId())
                .and("revision").is(activity.getRevision()));
        Update update = new Update()
                .set("externalId", activity.getExternalId())
                .set("actorExternalId", activity.getActorExternalId())
                .set("actorUniqueName", activity.getActorUniqueName())
                .set("actorUserId", activity.getActorUserId())
                .set("changedAt", activity.getChangedAt())
                .set("day", activity.getDay())
                .set("changedFields", activity.getChangedFields())
                .set("active", activity.isActive())
                .set("updatedAt", now)
                .setOnInsert("createdAt", now)
                .setOnInsert("version", 0L);
        WorkItemActivityDocument doc = mongoTemplate.findAndModify(query, update,
                FindAndModifyOptions.options().upsert(true).returnNew(true), WorkItemActivityDocument.class);
        return mapper.toDomain(doc);
    }

    @Override
    public OptionalInt maxRevisionByWorkItemId(String organizationId, String workItemId) {
        Query query = Query.query(Criteria.where("organizationId").is(organizationId)
                        .and("workItemId").is(workItemId))
                .with(Sort.by(Sort.Direction.DESC, "revision")).limit(1);
        WorkItemActivityDocument top = mongoTemplate.findOne(query, WorkItemActivityDocument.class);
        return top == null ? OptionalInt.empty() : OptionalInt.of(top.getRevision());
    }

    @Override
    public List<WorkItemActivity> findAllByWorkItemId(String organizationId, String workItemId) {
        return mongoRepository.findAllByOrganizationIdAndWorkItemId(organizationId, workItemId)
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<WorkItemActivity> findByOrganizationIdAndActorUserIdAndDay(String organizationId,
                                                                          String actorUserId, LocalDate day) {
        return mongoRepository.findAllByOrganizationIdAndActorUserIdAndDayAndActiveTrue(organizationId, actorUserId, day)
                .stream().map(mapper::toDomain).toList();
    }
}
