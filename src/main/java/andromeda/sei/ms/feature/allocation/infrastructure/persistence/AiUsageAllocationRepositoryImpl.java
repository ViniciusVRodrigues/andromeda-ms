package andromeda.sei.ms.feature.allocation.infrastructure.persistence;

import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocationRepository;
import andromeda.sei.ms.feature.allocation.domain.AllocationSplit;
import andromeda.sei.ms.feature.allocation.domain.ValidatedUserDay;
import andromeda.sei.ms.shared.infrastructure.persistence.ParallelChunks;
import andromeda.sei.ms.shared.infrastructure.persistence.RawDocuments;
import com.mongodb.client.MongoCollection;
import org.bson.Document;
import andromeda.sei.ms.feature.aiusage.infrastructure.persistence.AiUsageDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class AiUsageAllocationRepositoryImpl implements AiUsageAllocationRepository {

    private final AiUsageAllocationMongoRepository mongoRepository;
    private final AiUsageAllocationDocumentMapper mapper;
    private final MongoTemplate mongoTemplate;

    private static final String ORGANIZATION_ID = "organizationId";
    private static final String USER_ID = "userId";
    private static final String DAY = "day";
    private static final String STATUS = "status";
    private static final String ACTIVE = "active";
    private static final String AI_USAGE_IDS = "aiUsageIds";
    private static final String SPLIT_WORK_ITEM_ID = "splits.workItemId";
    private static final String SPLIT_PERCENTAGE = "splits.percentage";

    @Override
    public List<AiUsageAllocation> findByOrganizationIdAndUserIdAndDay(
            String organizationId, String userId, LocalDate day) {
        return mongoRepository.findByOrganizationIdAndUserIdAndDay(organizationId, userId, day)
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<AiUsageAllocation> findPendingByOrganizationIdAndUserId(
            String organizationId, String userId) {
        return mongoRepository.findByOrganizationIdAndUserIdAndStatusIn(
                        organizationId, userId,
                        List.of(AiUsageAllocation.Status.PENDING_VALIDATION, AiUsageAllocation.Status.UNALLOCATED))
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    public boolean existsValidatedByOrganizationIdAndUserIdAndDay(
            String organizationId, String userId, LocalDate day) {
        // AGENT allocations and the manual corrections moved from them (movedFromAgent) do not close the day
        Query query = Query.query(Criteria.where(ORGANIZATION_ID).is(organizationId).and(USER_ID).is(userId)
                .and(DAY).is(day).and("status").is(AiUsageAllocation.Status.VALIDATED)
                .and("assignedBy").ne(AiUsageAllocation.AssignedBy.AGENT).and("movedFromAgent").ne(true));
        return mongoTemplate.exists(query, AiUsageAllocationDocument.class);
    }

    @Override
    public void saveAll(List<AiUsageAllocation> allocations) {
        mongoRepository.saveAll(allocations.stream().map(mapper::toDocument).toList());
    }

    @Override
    public AiUsageAllocation save(AiUsageAllocation allocation) {
        AiUsageAllocationDocument saved = mongoRepository.save(mapper.toDocument(allocation));
        return mapper.toDomain(saved);
    }

    @Override
    public void deleteByOrganizationIdAndUserIdAndDay(
            String organizationId, String userId, LocalDate day) {
        mongoRepository.deleteByOrganizationIdAndUserIdAndDayAndAssignedByNot(
                organizationId, userId, day, AiUsageAllocation.AssignedBy.AGENT);
    }

    @Override
    public java.util.Set<LocalDate> findAgentDays(String organizationId, String userId, LocalDate from, LocalDate to) {
        Query query = Query.query(Criteria.where(ORGANIZATION_ID).is(organizationId).and(USER_ID).is(userId)
                .and("assignedBy").is(AiUsageAllocation.AssignedBy.AGENT).and(ACTIVE).is(true)
                .and(DAY).gte(from).lte(to));
        query.fields().include(DAY);
        return mongoTemplate.find(query, AiUsageAllocationDocument.class).stream()
                .map(AiUsageAllocationDocument::getDay).collect(java.util.stream.Collectors.toSet());
    }

    @Override
    public void deleteById(String id) {
        mongoRepository.deleteById(id);
    }

    @Override
    public List<AiUsageAllocation> findByOrgUserAndStatus(
            String organizationId, String userId, AiUsageAllocation.Status status) {
        return mongoRepository.findByOrganizationIdAndUserIdAndStatus(organizationId, userId, status)
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<AiUsageAllocation> findByOrgUserAndDay(
            String organizationId, String userId, LocalDate day) {
        return mongoRepository.findByOrganizationIdAndUserIdAndDay(organizationId, userId, day)
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    public void updateAllocationIdOnUsageRecords(List<String> aiUsageIds, String allocationId) {
        if (aiUsageIds == null || aiUsageIds.isEmpty()) {
            return;
        }
        Query query = Query.query(Criteria.where("_id").in(aiUsageIds));
        Update update = new Update()
                .set("allocationId", allocationId)
                .set("updatedAt", Instant.now());
        mongoTemplate.updateMulti(query, update, AiUsageDocument.class);
    }

    @Override
    public Optional<AiUsageAllocation> findById(String id, String organizationId) {
        return mongoRepository.findByIdAndOrganizationIdAndActiveTrue(id, organizationId)
                .map(mapper::toDomain);
    }

    @Override
    public List<AiUsageAllocation> findValidatedByOrgUserAndWorkItemId(
            String organizationId, String userId, String workItemId) {
        Criteria criteria = Criteria.where("organizationId").is(organizationId)
                .and("userId").is(userId)
                .and("status").is(AiUsageAllocation.Status.VALIDATED)
                .and("active").is(true)
                .and("splits.workItemId").is(workItemId);
        Query query = Query.query(criteria);
        return mongoTemplate.find(query, AiUsageAllocationDocument.class)
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<AiUsageAllocation> findValidatedByOrganizationIdAndDayBetween(
            String organizationId, LocalDate from, LocalDate to) {
        Query query = Query.query(Criteria.where("organizationId").is(organizationId)
                .and("status").is(AiUsageAllocation.Status.VALIDATED)
                .and("active").is(true)
                .and("day").gte(from).lte(to));
        return mongoTemplate.find(query, AiUsageAllocationDocument.class).stream().map(mapper::toDomain).toList();
    }

    /**
     * Raw read (see {@link RawDocuments}): a project's allocations of a long period can be tens of
     * thousands of documents with nested splits, and the mapping converter dominated the dashboard
     * request. Only the fields a cost breakdown reads are fetched; the returned allocations carry id,
     * organization, user, day, usage ids and splits (work item and percentage), status VALIDATED.
     */
    @Override
    public List<AiUsageAllocation> findValidatedByOrganizationIdAndWorkItemIdInAndDayBetween(
            String organizationId, java.util.Collection<String> workItemIds, LocalDate from, LocalDate to) {
        if (workItemIds == null || workItemIds.isEmpty()) return List.of();
        // An allocation splitting to work items of two chunks comes back twice: keep the first.
        Map<String, AiUsageAllocation> byId = new LinkedHashMap<>();
        ParallelChunks.read(workItemIds, chunk -> leanAllocations(organizationId, chunk, from, to))
                .forEach(a -> byId.putIfAbsent(a.getId(), a));
        return List.copyOf(byId.values());
    }

    private List<AiUsageAllocation> leanAllocations(String organizationId, List<String> workItemIds,
                                                    LocalDate from, LocalDate to) {
        Document filter = new Document(ORGANIZATION_ID, organizationId)
                .append(SPLIT_WORK_ITEM_ID, new Document("$in", workItemIds))
                .append(DAY, dayRange(from, to))
                .append(STATUS, AiUsageAllocation.Status.VALIDATED.name())
                .append(ACTIVE, true);
        Document projection = new Document(USER_ID, 1).append(DAY, 1).append(AI_USAGE_IDS, 1)
                .append(SPLIT_WORK_ITEM_ID, 1).append(SPLIT_PERCENTAGE, 1);
        List<AiUsageAllocation> allocations = new ArrayList<>();
        for (Document document : collection().find(filter).projection(projection)) {
            allocations.add(leanAllocation(document, organizationId, RawDocuments.day(mongoTemplate, document.get(DAY))));
        }
        return allocations;
    }

    static AiUsageAllocation leanAllocation(Document document, String organizationId, LocalDate day) {
        List<AllocationSplit> splits = RawDocuments.documents(document.get("splits")).stream()
                .map(split -> new AllocationSplit(split.getString("workItemId"), null,
                        RawDocuments.decimal(split.get("percentage"))))
                .toList();
        return new AiUsageAllocation(RawDocuments.id(document), organizationId, document.getString(USER_ID), day,
                RawDocuments.strings(document.get(AI_USAGE_IDS)), splits, AiUsageAllocation.Status.VALIDATED, null,
                true, null, null, null);
    }

    @Override
    public List<ValidatedUserDay> findValidatedUserDays(String organizationId, java.util.Collection<String> userIds,
                                                        LocalDate from, LocalDate to) {
        if (userIds == null || userIds.isEmpty()) return List.of();
        List<Document> pipeline = List.of(
                new Document("$match", new Document(ORGANIZATION_ID, organizationId)
                        .append(USER_ID, new Document("$in", List.copyOf(userIds)))
                        .append(DAY, dayRange(from, to))
                        .append(STATUS, AiUsageAllocation.Status.VALIDATED.name())
                        .append(ACTIVE, true)),
                new Document("$group", new Document("_id",
                        new Document(USER_ID, "$" + USER_ID).append(DAY, "$" + DAY))));
        List<ValidatedUserDay> days = new ArrayList<>();
        for (Document row : collection().aggregate(pipeline)) {
            Document key = row.get("_id", Document.class);
            days.add(new ValidatedUserDay(key.getString(USER_ID), RawDocuments.day(mongoTemplate, key.get(DAY))));
        }
        return days;
    }

    private Document dayRange(LocalDate from, LocalDate to) {
        return new Document("$gte", RawDocuments.storedDay(mongoTemplate, from))
                .append("$lte", RawDocuments.storedDay(mongoTemplate, to));
    }

    private MongoCollection<Document> collection() {
        return mongoTemplate.getCollection(mongoTemplate.getCollectionName(AiUsageAllocationDocument.class));
    }

    @Override
    public List<AiUsageAllocation> findValidatedByOrganizationIdAndUserIdInAndDayBetween(
            String organizationId, java.util.Collection<String> userIds, LocalDate from, LocalDate to) {
        if (userIds == null || userIds.isEmpty()) return List.of();
        Query query = Query.query(Criteria.where("organizationId").is(organizationId)
                .and("userId").in(userIds)
                .and("day").gte(from).lte(to)
                .and("status").is(AiUsageAllocation.Status.VALIDATED)
                .and("active").is(true));
        return mongoTemplate.find(query, AiUsageAllocationDocument.class).stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<AiUsageAllocation> findValidatedByOrganizationIdAndWorkItemIdIn(
            String organizationId, java.util.Collection<String> workItemIds) {
        if (workItemIds == null || workItemIds.isEmpty()) return List.of();
        Query query = Query.query(Criteria.where("organizationId").is(organizationId)
                .and("status").is(AiUsageAllocation.Status.VALIDATED)
                .and("active").is(true)
                .and("splits.workItemId").in(workItemIds));
        return mongoTemplate.find(query, AiUsageAllocationDocument.class).stream().map(mapper::toDomain).toList();
    }

    @Override
    public Page<AiUsageAllocation> findValidatedByOrgUserAndOptionalMonth(
            String organizationId, String userId, YearMonth month, Pageable pageable) {
        Criteria criteria = Criteria.where("organizationId").is(organizationId)
                .and("userId").is(userId)
                .and("status").is(AiUsageAllocation.Status.VALIDATED)
                .and("active").is(true);

        if (month != null) {
            LocalDate from = month.atDay(1);
            LocalDate to = month.atEndOfMonth();
            criteria = criteria.and("day").gte(from).lte(to);
        }

        Query countQuery = Query.query(criteria);
        long total = mongoTemplate.count(countQuery, AiUsageAllocationDocument.class);

        Query dataQuery = Query.query(criteria)
                .with(Sort.by(Sort.Direction.DESC, "day"))
                .skip(pageable.getOffset())
                .limit(pageable.getPageSize());

        List<AiUsageAllocation> results = mongoTemplate
                .find(dataQuery, AiUsageAllocationDocument.class)
                .stream().map(mapper::toDomain).toList();

        return new PageImpl<>(results, pageable, total);
    }
}