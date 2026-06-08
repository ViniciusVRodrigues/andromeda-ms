package andromeda.sei.ms.feature.workitem.infrastructure.persistence;

import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.domain.WorkItemRepository;
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
public class WorkItemRepositoryImpl implements WorkItemRepository {

    private final WorkItemMongoRepository mongoRepository;
    private final WorkItemDocumentMapper mapper;
    private final MongoTemplate mongoTemplate;

    @Override
    public WorkItem save(WorkItem workItem) {
        WorkItemDocument doc = mapper.toDocument(workItem);
        Instant now = Instant.now();
        doc.setUpdatedAt(now);
        if (doc.getCreatedAt() == null) {
            doc.setCreatedAt(now);
        }

        Query query = Query.query(Criteria.where("organizationId").is(workItem.getOrganizationId())
                .and("externalId").is(workItem.getExternalId())
                .and("source").is(workItem.getSource()));

        Update update = new Update()
                .set("projectId", doc.getProjectId())
                .set("source", doc.getSource())
                .set("externalUpdatedAt", doc.getExternalUpdatedAt())
                .set("title", doc.getTitle())
                .set("rawType", doc.getRawType())
                .set("resolvedType", doc.getResolvedType())
                .set("rawStatus", doc.getRawStatus())
                .set("resolvedStatus", doc.getResolvedStatus())
                .set("priority", doc.getPriority())
                .set("storyPoints", doc.getStoryPoints())
                .set("labels", doc.getLabels())
                .set("sprintId", doc.getSprintId())
                .set("assigneeExternalId", doc.getAssigneeExternalId())
                .set("assigneeUserId", doc.getAssigneeUserId())
                .set("startedAt", doc.getStartedAt())
                .set("completedAt", doc.getCompletedAt())
                .set("active", doc.isActive())
                .set("updatedAt", now)
                .setOnInsert("organizationId", doc.getOrganizationId())
                .setOnInsert("externalId", doc.getExternalId())
                .setOnInsert("createdAt", doc.getCreatedAt() != null ? doc.getCreatedAt() : now);

        mongoTemplate.upsert(query, update, WorkItemDocument.class);

        return findByOrganizationIdAndExternalIdAndSource(
                workItem.getOrganizationId(), workItem.getExternalId(), workItem.getSource())
                .orElseThrow();
    }

    @Override
    public Optional<WorkItem> findByOrganizationIdAndExternalIdAndSource(
            String organizationId, String externalId, WorkItem.Source source) {
        return mongoRepository.findByOrganizationIdAndExternalIdAndSource(organizationId, externalId, source)
                .map(mapper::toDomain);
    }

    @Override
    public List<WorkItem> findAllByOrganizationIdAndProjectId(String organizationId, String projectId) {
        return mongoRepository.findAllByOrganizationIdAndProjectId(organizationId, projectId)
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    public void updateMetrics(String id, Double cycleTimeHours, Double leadTimeHours) {
        Query query = Query.query(Criteria.where("_id").is(id));
        Update update = new Update()
                .set("cycleTimeHours", cycleTimeHours)
                .set("leadTimeHours", leadTimeHours)
                .set("updatedAt", Instant.now());
        mongoTemplate.updateFirst(query, update, WorkItemDocument.class);
    }
}
