package andromeda.sei.ms.feature.allocation.infrastructure.persistence;

import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocationRepository;
import andromeda.sei.ms.feature.allocation.domain.AllocationCoverage;
import andromeda.sei.ms.feature.allocation.domain.AllocationCoverageDetail;
import andromeda.sei.ms.feature.allocation.domain.AllocationSplit;
import andromeda.sei.ms.feature.allocation.domain.ValidatedUserDay;
import andromeda.sei.ms.shared.infrastructure.persistence.ParallelChunks;
import andromeda.sei.ms.shared.infrastructure.persistence.RawDocuments;
import com.mongodb.MongoCommandException;
import com.mongodb.client.MongoCollection;
import org.bson.Document;
import org.bson.types.ObjectId;
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

import java.math.BigDecimal;
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
    private static final String ALLOCATION_ID = "allocationId";
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

    /**
     * Cited records the exact path reads in one request (id list plus 6 fields each, as the main did); above
     * it the stamp scan serves the allocations and only the inconsistent ones take the exact path.
     */
    static final int EXACT_PATH_MAX_CITED = 50_000;

    /** Package-private so the tests force either path. */
    int exactPathMaxCited = EXACT_PATH_MAX_CITED;
    /** Allocations that failed the stamp check and took the exact path (PERF2 D7); read by the tests. */
    final java.util.concurrent.atomic.AtomicLong fallbackAllocations = new java.util.concurrent.atomic.AtomicLong();
    /** Scans of the usage stamp; read by the tests. */
    final java.util.concurrent.atomic.AtomicLong stampScans = new java.util.concurrent.atomic.AtomicLong();

    @Override
    public List<AllocationCoverage> summarizeValidatedCoverage(String organizationId,
            java.util.Collection<String> workItemIds, LocalDate from, LocalDate to) {
        if (workItemIds == null || workItemIds.isEmpty()) return List.of();
        List<AllocationCoverage> rows = new ArrayList<>();
        for (StampedAllocation a : covered(organizationId, allocationSignatures(organizationId, workItemIds, from, to))) {
            BigDecimal cost = BigDecimal.ZERO;
            long tokens = 0;
            for (UsageRow u : a.usage()) {
                cost = cost.add(u.cost());
                tokens += u.input() + u.output();
            }
            rows.add(new AllocationCoverage(a.id(), a.userId(), a.day(), a.splits(), cost, tokens));
        }
        return rows;
    }

    @Override
    public List<AllocationCoverageDetail> summarizeValidatedCoverageByDayAndModel(String organizationId,
            java.util.Collection<String> workItemIds, LocalDate from, LocalDate to) {
        if (workItemIds == null || workItemIds.isEmpty()) return List.of();
        List<AllocationCoverageDetail> rows = new ArrayList<>();
        for (StampedAllocation a : covered(organizationId, allocationSignatures(organizationId, workItemIds, from, to))) {
            for (UsageRow u : a.usage()) {
                rows.add(new AllocationCoverageDetail(a.id(), a.userId(), a.splits(), u.day(), u.model(),
                        u.input(), u.output(), u.interactions(), u.cost()));
            }
        }
        return rows;
    }

    /**
     * The usage of each allocation, by the rule of the cited list. Few cited records: the exact path for all.
     * Many: the stamp scan, and the allocations whose signature does not match take the exact path.
     */
    private List<StampedAllocation> covered(String organizationId, List<AllocationSig> allocations) {
        if (allocations.isEmpty()) return List.of();
        long cited = allocations.stream().mapToLong(AllocationSig::n).sum();
        if (cited <= exactPathMaxCited) return exactUsage(organizationId, allocations);
        stampScans.incrementAndGet();
        Stamped stamped = stampedUsage(allocations, organizationId);
        fallbackAllocations.addAndGet(stamped.inconsistent().size());
        List<StampedAllocation> all = new ArrayList<>(stamped.consistent());
        all.addAll(exactUsage(organizationId, stamped.inconsistent()));
        return all;
    }

    private record UsageFields(LocalDate day, String model, BigDecimal cost, long input, long output,
                               long interactions) {}

    /** Key of a cited id as the old {@code $convert} saw it: valid hex (any case) is an ObjectId, the rest a string. */
    private static String citedKey(String id) {
        return ObjectId.isValid(id) ? new ObjectId(id).toHexString() : "s:" + id;
    }

    /** Key of a stored {@code _id}: an ObjectId by its hex; a string {@code _id} never matched a converted key. */
    private static String storedKey(Object id) {
        return id instanceof ObjectId objectId ? objectId.toHexString() : "s:" + id;
    }

    /** Exact rule: walk each cited list; a repeated id counts twice; a missing, inactive or foreign one, never. */
    private List<StampedAllocation> exactUsage(String organizationId, List<AllocationSig> allocations) {
        if (allocations.isEmpty()) return List.of();
        List<String> allocationIds = allocations.stream().map(AllocationSig::id).toList();
        Map<String, List<String>> citedByAllocation = new java.util.HashMap<>();
        ParallelChunks.read(allocationIds, chunk -> {
            List<Document> found = new ArrayList<>();
            Document filter = new Document(ORGANIZATION_ID, organizationId)
                    .append("_id", new Document("$in", RawDocuments.storedIds(chunk)));
            for (Document document : collection().find(filter).projection(new Document(AI_USAGE_IDS, 1))) {
                found.add(document);
            }
            return found;
        }).forEach(d -> citedByAllocation.put(RawDocuments.id(d), RawDocuments.strings(d.get(AI_USAGE_IDS))));

        Map<String, String> idByKey = new java.util.HashMap<>();
        citedByAllocation.values().forEach(list -> list.stream().filter(java.util.Objects::nonNull)
                .forEach(id -> idByKey.putIfAbsent(citedKey(id), id)));
        Map<String, UsageFields> usageByKey = new java.util.HashMap<>();
        Document projection = new Document("day", 1).append("model", 1).append("inputTokens", 1)
                .append("outputTokens", 1).append("interactions", 1).append("costUsd", 1);
        ParallelChunks.read(idByKey.values(), chunk -> {
            List<Document> found = new ArrayList<>();
            Document filter = new Document(ORGANIZATION_ID, organizationId)
                    .append("_id", new Document("$in", RawDocuments.storedIds(chunk))).append(ACTIVE, true);
            for (Document document : mongoTemplate.getCollection(mongoTemplate.getCollectionName(AiUsageDocument.class))
                    .find(filter).projection(projection)) {
                found.add(document);
            }
            return found;
        }).forEach(d -> usageByKey.putIfAbsent(storedKey(d.get("_id")), new UsageFields(
                RawDocuments.day(mongoTemplate, d.get(DAY)), d.getString("model"),
                orZero(RawDocuments.decimal(d.get("costUsd"))), RawDocuments.longValue(d.get("inputTokens")),
                RawDocuments.longValue(d.get("outputTokens")), RawDocuments.longValue(d.get("interactions")))));

        List<StampedAllocation> result = new ArrayList<>();
        for (AllocationSig a : allocations) {
            Map<List<Object>, UsageRow> perDayAndModel = new LinkedHashMap<>();
            for (String id : citedByAllocation.getOrDefault(a.id(), List.of())) {
                UsageFields u = id == null ? null : usageByKey.get(citedKey(id));
                if (u == null) continue;
                perDayAndModel.merge(java.util.Arrays.asList(u.day(), u.model()),
                        new UsageRow(u.day(), u.model(), 1, 0, 0, u.cost(), u.input(), u.output(), u.interactions()),
                        (x, y) -> new UsageRow(x.day(), x.model(), 0, 0, 0, x.cost().add(y.cost()),
                                x.input() + y.input(), x.output() + y.output(), x.interactions() + y.interactions()));
            }
            result.add(new StampedAllocation(a.id(), a.userId(), a.day(), a.splits(),
                    List.copyOf(perDayAndModel.values())));
        }
        return result;
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /** Validated, active allocations of the organization that split to the work items, within the optional days. */
    private Document allocationMatch(String organizationId, List<String> workItemIds, LocalDate from, LocalDate to) {
        Document match = new Document(ORGANIZATION_ID, organizationId)
                .append(SPLIT_WORK_ITEM_ID, new Document("$in", workItemIds))
                .append(STATUS, AiUsageAllocation.Status.VALIDATED.name())
                .append(ACTIVE, true);
        if (from != null || to != null) {
            Document range = new Document();
            if (from != null) range.append("$gte", RawDocuments.storedDay(mongoTemplate, from));
            if (to != null) range.append("$lte", RawDocuments.storedDay(mongoTemplate, to));
            match.append(DAY, range);
        }
        return match;
    }

    private static Document sumOrZero(String field) {
        return new Document("$sum", new Document("$ifNull", java.util.Arrays.asList("$" + field, 0)));
    }

    // --- PERF2 D7: coverage by the usage stamp, checked against the cited list by a hash signature ---

    private static final String NUM = "n";
    private static final String H1 = "h1";
    private static final String H2 = "h2";
    private static final String SIG = "sig";
    private static final int P1 = 2147483647;
    private static final int P2 = 2147483629;

    private record UsageRow(LocalDate day, String model, long n, long h1, long h2,
                            BigDecimal cost, long input, long output, long interactions) {}

    private record AllocationSig(String id, String userId, LocalDate day, List<AllocationSplit> splits,
                                 long n, long h1, long h2) {}

    private record StampedAllocation(String id, String userId, LocalDate day, List<AllocationSplit> splits,
                                     List<UsageRow> usage) {}

    /** Allocations whose cited list equals their stamped records, with those records; and the ids that do not. */
    private record Stamped(List<StampedAllocation> consistent, List<AllocationSig> inconsistent) {}

    /** One id's term of a hash sum, as a long so the sums never turn into doubles. */
    private static Document hashTerm(Object key, int prime) {
        return new Document("$toLong", new Document("$mod", java.util.Arrays.asList(
                new Document("$toHashedIndexKey", key), prime)));
    }

    /** Signature of the cited list: count and two hash sums over the ids in the stored form (as the old usageKey). */
    private static Document citedSignature() {
        Document key = new Document("$convert", new Document("input", "$$this").append("to", "objectId")
                .append("onError", "$$this").append("onNull", "$$this"));
        return new Document("$reduce", new Document("input",
                new Document("$ifNull", java.util.Arrays.asList("$" + AI_USAGE_IDS, List.of())))
                .append("initialValue", new Document(NUM, 0L).append(H1, 0L).append(H2, 0L))
                .append("in", new Document(NUM, new Document("$add", java.util.Arrays.asList("$$value." + NUM, 1L)))
                        .append(H1, new Document("$add", java.util.Arrays.asList("$$value." + H1, hashTerm(key, P1))))
                        .append(H2, new Document("$add", java.util.Arrays.asList("$$value." + H2, hashTerm(key, P2))))));
    }

    List<Document> allocationSignaturePipeline(String organizationId, List<String> workItemIds,
                                               LocalDate from, LocalDate to) {
        return List.of(
                new Document("$match", allocationMatch(organizationId, workItemIds, from, to)),
                new Document("$project", new Document(USER_ID, 1).append(DAY, 1).append(SPLIT_WORK_ITEM_ID, 1)
                        .append(SPLIT_PERCENTAGE, 1).append(SIG, citedSignature())));
    }

    /** Usage of the allocations' stamp, grouped by (allocation, day, model), with the signature of the ids. */
    private List<Document> stampedUsagePipeline(String organizationId, java.util.Collection<String> userIds,
                                                LocalDate minDay, LocalDate maxDay) {
        return List.of(
                new Document("$match", new Document(ORGANIZATION_ID, organizationId)
                        .append(USER_ID, new Document("$in", List.copyOf(userIds)))
                        .append(ACTIVE, true)
                        .append(DAY, new Document("$gte", RawDocuments.storedDay(mongoTemplate, minDay))
                                .append("$lte", RawDocuments.storedDay(mongoTemplate, maxDay)))),
                new Document("$group", new Document("_id", new Document(ALLOCATION_ID, "$" + ALLOCATION_ID)
                        .append(DAY, "$" + DAY).append("model", "$model"))
                        .append(NUM, new Document("$sum", 1))
                        .append(H1, new Document("$sum", hashTerm("$_id", P1)))
                        .append(H2, new Document("$sum", hashTerm("$_id", P2)))
                        .append("cost", sumOrZero("costUsd"))
                        .append("input", sumOrZero("inputTokens"))
                        .append("output", sumOrZero("outputTokens"))
                        .append("interactions", sumOrZero("interactions"))));
    }

    /** Same hint-and-retry as the dashboard sums of the usage repository (PERF2 D6). */
    private List<Document> aggregateUsageCovered(List<Document> pipeline) {
        MongoCollection<Document> usages = mongoTemplate.getCollection(
                mongoTemplate.getCollectionName(AiUsageDocument.class));
        try {
            return usages.aggregate(pipeline).hintString(AiUsageDocument.DASHBOARD_COVER_INDEX).into(new ArrayList<>());
        } catch (MongoCommandException e) {
            if (e.getMessage() == null || !e.getMessage().toLowerCase().contains("hint")) throw e;
            return usages.aggregate(pipeline).into(new ArrayList<>());
        }
    }

    /** Signatures of the allocations, per chunk of work items; an allocation in two chunks keeps its first row. */
    private List<AllocationSig> allocationSignatures(String organizationId, java.util.Collection<String> workItemIds,
                                                     LocalDate from, LocalDate to) {
        List<AllocationSig> all = ParallelChunks.read(workItemIds, chunk -> {
            List<AllocationSig> rows = new ArrayList<>();
            for (Document row : collection().aggregate(allocationSignaturePipeline(organizationId, chunk, from, to))) {
                Document sig = row.get(SIG, Document.class);
                rows.add(new AllocationSig(RawDocuments.id(row), row.getString(USER_ID),
                        RawDocuments.day(mongoTemplate, row.get(DAY)), splitsOf(row.get("splits")),
                        RawDocuments.longValue(sig.get(NUM)), RawDocuments.longValue(sig.get(H1)),
                        RawDocuments.longValue(sig.get(H2))));
            }
            return rows;
        });
        Map<String, AllocationSig> byId = new LinkedHashMap<>();
        all.forEach(a -> byId.putIfAbsent(a.id(), a));
        return List.copyOf(byId.values());
    }

    /** One scan of the stamp for all the allocations; rows of other allocations (or none) are dropped. */
    private Stamped stampedUsage(List<AllocationSig> allocations, String organizationId) {
        if (allocations.isEmpty()) return new Stamped(List.of(), List.of());
        java.util.Set<String> users = new java.util.LinkedHashSet<>();
        java.util.Set<String> known = new java.util.HashSet<>();
        LocalDate minDay = null;
        LocalDate maxDay = null;
        for (AllocationSig a : allocations) {
            users.add(a.userId());
            known.add(a.id());
            if (a.day() == null) continue;
            if (minDay == null || a.day().isBefore(minDay)) minDay = a.day();
            if (maxDay == null || a.day().isAfter(maxDay)) maxDay = a.day();
        }
        Map<String, List<UsageRow>> usageByAllocation = new java.util.HashMap<>();
        if (minDay != null) {
            for (Document row : aggregateUsageCovered(stampedUsagePipeline(organizationId, users, minDay, maxDay))) {
                Document key = row.get("_id", Document.class);
                String allocationId = key.getString(ALLOCATION_ID);
                if (allocationId == null || !known.contains(allocationId)) continue;
                usageByAllocation.computeIfAbsent(allocationId, k -> new ArrayList<>())
                        .add(new UsageRow(RawDocuments.day(mongoTemplate, key.get(DAY)), key.getString("model"),
                                RawDocuments.longValue(row.get(NUM)), RawDocuments.longValue(row.get(H1)),
                                RawDocuments.longValue(row.get(H2)), RawDocuments.decimal(row.get("cost")),
                                RawDocuments.longValue(row.get("input")), RawDocuments.longValue(row.get("output")),
                                RawDocuments.longValue(row.get("interactions"))));
            }
        }

        List<StampedAllocation> consistent = new ArrayList<>();
        List<AllocationSig> inconsistent = new ArrayList<>();
        for (AllocationSig a : allocations) {
            List<UsageRow> rows = usageByAllocation.getOrDefault(a.id(), List.of());
            long n = 0;
            long h1 = 0;
            long h2 = 0;
            for (UsageRow u : rows) {
                n += u.n();
                h1 += u.h1();
                h2 += u.h2();
            }
            if (a.day() != null && n == a.n() && h1 == a.h1() && h2 == a.h2()) {
                consistent.add(new StampedAllocation(a.id(), a.userId(), a.day(), a.splits(), rows));
            } else {
                inconsistent.add(a);
            }
        }
        return new Stamped(consistent, inconsistent);
    }

    private static List<AllocationSplit> splitsOf(Object splits) {
        return RawDocuments.documents(splits).stream()
                .map(split -> new AllocationSplit(split.getString("workItemId"), null,
                        RawDocuments.decimal(split.get("percentage"))))
                .toList();
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