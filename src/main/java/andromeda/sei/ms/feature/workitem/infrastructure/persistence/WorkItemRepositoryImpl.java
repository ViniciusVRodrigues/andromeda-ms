package andromeda.sei.ms.feature.workitem.infrastructure.persistence;

import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.domain.WorkItemPageResult;
import andromeda.sei.ms.feature.workitem.domain.WorkItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class WorkItemRepositoryImpl implements WorkItemRepository {

    private final WorkItemMongoRepository mongoRepository;
    private final WorkItemDocumentMapper mapper;
    private final MongoTemplate mongoTemplate;

    @Override
    public Optional<WorkItem> findByIdAndOrganizationIdAndActiveTrue(String id, String organizationId) {
        return mongoRepository.findByIdAndOrganizationIdAndActiveTrue(id, organizationId).map(mapper::toDomain);
    }

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
                .set("labels", doc.getLabels())
                .set("sprintId", doc.getSprintId())
                .set("sprintName", doc.getSprintName())
                .set("assigneeExternalId", doc.getAssigneeExternalId())
                .set("assigneeUserId", doc.getAssigneeUserId())
                .set("assigneeEmail", doc.getAssigneeEmail())
                .set("parentExternalId", doc.getParentExternalId())
                .set("startedAt", doc.getStartedAt())
                .set("completedAt", doc.getCompletedAt())
                .set("active", doc.isActive())
                .set("deactivatedAt", doc.getDeactivatedAt())
                .set("deactivationReason", doc.getDeactivationReason())
                .set("complexity", doc.getComplexity())
                .set("complexitySource", doc.getComplexitySource())
                .set("complexityOverriddenBy", doc.getComplexityOverriddenBy())
                .set("complexityOverriddenAt", doc.getComplexityOverriddenAt())
                .set("effort", doc.getEffort())
                .set("effortSource", doc.getEffortSource())
                .set("effortOverriddenBy", doc.getEffortOverriddenBy())
                .set("effortOverriddenAt", doc.getEffortOverriddenAt())
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
    public Optional<WorkItem> findById(String id) {
        return mongoRepository.findById(id).map(mapper::toDomain);
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

    /** Raw read of {@code _id} only (no document mapping), by the {@code {organizationId, projectId}} index. */
    @Override
    public java.util.Set<String> findActiveIdsByOrganizationIdAndProjectId(String organizationId, String projectId) {
        java.util.Set<String> ids = new java.util.HashSet<>();
        org.bson.Document filter = new org.bson.Document("organizationId", organizationId)
                .append("projectId", projectId).append("active", true);
        for (org.bson.Document document : mongoTemplate.getCollection(mongoTemplate.getCollectionName(WorkItemDocument.class))
                .find(filter).projection(new org.bson.Document("_id", 1))) {
            ids.add(document.get("_id").toString());
        }
        return ids;
    }

    @Override
    public List<WorkItem> findByOrganizationIdAndSprintIdAndAssigneeUserIdAndResolvedStatus(
            String organizationId, String sprintId, String assigneeUserId, WorkItem.ResolvedStatus status) {
        return mongoRepository.findByOrganizationIdAndSprintIdAndAssigneeUserIdAndResolvedStatus(
                        organizationId, sprintId, assigneeUserId, status)
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

    @Override
    public void updateAiRatios(String id, Double tokensPerPoint, Double tokensPerHour) {
        Query query = Query.query(Criteria.where("_id").is(id));
        Update update = new Update()
                .set("tokensPerPoint", tokensPerPoint)
                .set("tokensPerHour", tokensPerHour)
                .set("updatedAt", Instant.now());
        mongoTemplate.updateFirst(query, update, WorkItemDocument.class);
    }

    @Override
    public List<WorkItem> findByOrganizationIdAndAssigneeUserIdAndSprintId(
            String organizationId, String userId, String sprintId) {
        return mongoRepository.findByOrganizationIdAndAssigneeUserIdAndSprintId(organizationId, userId, sprintId)
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<WorkItem> findByOrganizationIdAndAssigneeUserIdAndSprintIdAndProjectId(
            String organizationId, String assigneeUserId, String sprintId, String projectId) {
        return mongoRepository.findByOrganizationIdAndAssigneeUserIdAndSprintIdAndProjectId(
                        organizationId, assigneeUserId, sprintId, projectId)
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<WorkItem> findAllByOrganizationIdAndProjectIdAndSprintId(
            String organizationId, String projectId, String sprintId) {
        return mongoRepository.findAllByOrganizationIdAndProjectIdAndSprintId(organizationId, projectId, sprintId)
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    public void updateAssigneeUserIdByExternalIdOrEmail(String organizationId, String azureId, String email, String userId) {
        List<Criteria> orCriteria = new java.util.ArrayList<>();
        if (azureId != null) {
            orCriteria.add(Criteria.where("assigneeExternalId").is(azureId));
        }
        if (email != null) {
            orCriteria.add(Criteria.where("assigneeEmail").is(email));
        }
        if (orCriteria.isEmpty()) {
            return;
        }
        Criteria criteria = Criteria.where("organizationId").is(organizationId)
                .and("active").is(true)
                .orOperator(orCriteria.toArray(new Criteria[0]));
        Query query = new Query(criteria);
        Update update = new Update().set("assigneeUserId", userId).set("updatedAt", Instant.now());
        mongoTemplate.updateMulti(query, update, WorkItemDocument.class);
    }

    @Override
    public WorkItemPageResult findRootCandidatesByOrganizationIdAndProjectId(
            String organizationId, String projectId, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size);
        List<WorkItem> content = mongoRepository
                .findByOrganizationIdAndProjectIdAndActiveTrueOrderByExternalIdAsc(organizationId, projectId, pageable)
                .stream().map(mapper::toDomain).toList();
        long totalElements = mongoRepository.countByOrganizationIdAndProjectIdAndActiveTrue(organizationId, projectId);
        return new WorkItemPageResult(content, page, size, totalElements);
    }

    @Override
    public List<WorkItem> findByOrganizationIdAndProjectIdAndParentExternalIdIn(
            String organizationId, String projectId, List<String> parentExternalIds) {
        if (parentExternalIds == null || parentExternalIds.isEmpty()) {
            return List.of();
        }
        return mongoRepository
                .findByOrganizationIdAndProjectIdAndParentExternalIdInAndActiveTrue(organizationId, projectId, parentExternalIds)
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<WorkItem> findChildCandidatesByOrganizationIdAndParentExternalId(
            String organizationId, String projectId, String parentExternalId, int limit) {
        PageRequest pageable = PageRequest.of(0, limit);
        return mongoRepository
                .findByOrganizationIdAndProjectIdAndParentExternalIdAndActiveTrueOrderByExternalIdAsc(
                        organizationId, projectId, parentExternalId, pageable)
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<WorkItem> findAllActiveByOrganizationIdAndExternalId(String organizationId, String externalId) {
        if (externalId == null || externalId.isBlank()) return List.of();
        return mongoRepository.findAllByOrganizationIdAndExternalIdAndActiveTrue(organizationId, externalId)
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<WorkItem> findAllByOrganizationIdAndExternalIdInAndSource(
            String organizationId, List<String> externalIds, WorkItem.Source source) {
        if (externalIds == null || externalIds.isEmpty()) return List.of();
        return mongoRepository
                .findAllByOrganizationIdAndExternalIdInAndSource(organizationId, externalIds, source)
                .stream().map(mapper::toDomain).toList();
    }

    /** Raw projection read (no document mapping), by the {@code {organizationId, projectId}} index. */
    @Override
    public List<WorkItem> findMetricFieldsByOrganizationIdAndProjectId(String organizationId, String projectId) {
        org.bson.Document filter = new org.bson.Document("organizationId", organizationId)
                .append("projectId", projectId).append("active", true);
        org.bson.Document projection = new org.bson.Document("externalId", 1).append("parentExternalId", 1)
                .append("sprintId", 1).append("resolvedType", 1).append("resolvedStatus", 1).append("effort", 1)
                .append("complexity", 1).append("completedAt", 1).append("leadTimeHours", 1)
                .append("cycleTimeHours", 1);
        List<WorkItem> items = new java.util.ArrayList<>();
        for (org.bson.Document d : mongoTemplate.getCollection(mongoTemplate.getCollectionName(WorkItemDocument.class))
                .find(filter).projection(projection)) {
            items.add(leanItem(d, organizationId, projectId));
        }
        return items;
    }

    static WorkItem leanItem(org.bson.Document d, String organizationId, String projectId) {
        return new WorkItem(d.get("_id").toString(), organizationId, projectId, null, d.getString("externalId"), null,
                null, null, enumOf(WorkItem.ResolvedType.class, d.getString("resolvedType")), null,
                enumOf(WorkItem.ResolvedStatus.class, d.getString("resolvedStatus")), null, null,
                d.getString("sprintId"), null, null, null, d.getString("parentExternalId"), null, null,
                instant(d.get("completedAt")), number(d.get("cycleTimeHours")), number(d.get("leadTimeHours")),
                true, null, null, number(d.get("complexity")), null, null, null, number(d.get("effort")), null, null, null);
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

    @Override
    public List<WorkItem> findActiveByOrganizationIdAndProjectIdAndSprintIdIn(String organizationId, String projectId,
                                                                              Collection<String> sprintIds) {
        if (sprintIds == null || sprintIds.isEmpty()) return List.of();
        return mongoRepository.findAllByOrganizationIdAndProjectIdAndSprintIdInAndActiveTrue(organizationId, projectId,
                sprintIds).stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<WorkItem> findActiveByOrganizationIdAndProjectIdAndCompletedAtInRange(String organizationId,
                                                                                      String projectId,
                                                                                      Instant from, Instant to) {
        return mongoRepository.findActiveCompletedInRange(organizationId, projectId, from, to)
                .stream().map(mapper::toDomain).toList();
    }

    private static final int EXTERNAL_ID_BATCH = 1000;

    @Override
    public java.util.Set<String> findActiveExternalIdsByOrganizationIdAndProjectIdAndSource(
            String organizationId, String projectId, WorkItem.Source source) {
        org.bson.Document filter = new org.bson.Document("organizationId", organizationId)
                .append("projectId", projectId).append("source", source.name()).append("active", true);
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (org.bson.Document d : mongoTemplate.getCollection(mongoTemplate.getCollectionName(WorkItemDocument.class))
                .find(filter).projection(new org.bson.Document("externalId", 1))) {
            ids.add(d.getString("externalId"));
        }
        return ids;
    }

    @Override
    public java.util.Set<String> findInactiveExternalIdsByOrganizationIdAndSourceAndExternalIdIn(
            String organizationId, WorkItem.Source source, Collection<String> externalIds) {
        java.util.Set<String> found = new java.util.HashSet<>();
        if (externalIds == null || externalIds.isEmpty()) return found;
        List<String> all = List.copyOf(externalIds);
        for (int i = 0; i < all.size(); i += EXTERNAL_ID_BATCH) {
            List<String> batch = all.subList(i, Math.min(i + EXTERNAL_ID_BATCH, all.size()));
            org.bson.Document filter = new org.bson.Document("organizationId", organizationId)
                    .append("source", source.name()).append("active", false)
                    .append("externalId", new org.bson.Document("$in", batch));
            for (org.bson.Document d : mongoTemplate.getCollection(mongoTemplate.getCollectionName(WorkItemDocument.class))
                    .find(filter).projection(new org.bson.Document("externalId", 1))) {
                found.add(d.getString("externalId"));
            }
        }
        return found;
    }

    @Override
    public long deactivateByExternalIds(String organizationId, String projectId, WorkItem.Source source,
                                        Collection<String> externalIds, WorkItem.DeactivationReason reason,
                                        Instant at) {
        if (externalIds == null || externalIds.isEmpty()) return 0;
        List<String> all = List.copyOf(externalIds);
        long changed = 0;
        for (int i = 0; i < all.size(); i += EXTERNAL_ID_BATCH) {
            List<String> batch = all.subList(i, Math.min(i + EXTERNAL_ID_BATCH, all.size()));
            Query query = Query.query(Criteria.where("organizationId").is(organizationId)
                    .and("projectId").is(projectId)
                    .and("source").is(source)
                    .and("externalId").in(batch)
                    .and("active").is(true));
            Update update = new Update()
                    .set("active", false)
                    .set("deactivatedAt", at)
                    .set("deactivationReason", reason)
                    .set("updatedAt", at)
                    .inc("version", 1);
            changed += mongoTemplate.updateMulti(query, update, WorkItemDocument.class).getModifiedCount();
        }
        return changed;
    }
}
