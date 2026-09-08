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
                .set("storyPoints", doc.getStoryPoints())
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
    public void updateTokensPerSp(String id, Double tokensPerSp) {
        Query query = Query.query(Criteria.where("_id").is(id));
        Update update = new Update()
                .set("tokensPerSp", tokensPerSp)
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
    public List<WorkItem> findAllByOrganizationIdAndExternalIdInAndSource(
            String organizationId, List<String> externalIds, WorkItem.Source source) {
        if (externalIds == null || externalIds.isEmpty()) return List.of();
        return mongoRepository
                .findAllByOrganizationIdAndExternalIdInAndSource(organizationId, externalIds, source)
                .stream().map(mapper::toDomain).toList();
    }
}
