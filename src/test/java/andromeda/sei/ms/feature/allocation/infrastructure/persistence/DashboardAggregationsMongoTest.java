package andromeda.sei.ms.feature.allocation.infrastructure.persistence;

import andromeda.sei.ms.feature.aiusage.domain.AIUsage;
import andromeda.sei.ms.feature.aiusage.infrastructure.persistence.AiUsageDocument;
import andromeda.sei.ms.feature.aiusage.infrastructure.persistence.AiUsageDocumentMapper;
import andromeda.sei.ms.feature.aiusage.infrastructure.persistence.AiUsageMongoRepository;
import andromeda.sei.ms.feature.aiusage.infrastructure.persistence.AiUsageRepositoryImpl;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation;
import andromeda.sei.ms.feature.allocation.domain.AllocationCoverage;
import andromeda.sei.ms.feature.allocation.domain.AllocationCoverageDetail;
import andromeda.sei.ms.feature.allocation.domain.AllocationSplit;
import andromeda.sei.ms.shared.testsupport.MongoIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.Parameter;
import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/** perf2 P1: allocation coverage computed in Mongo equals the Java loop the dashboards run today. */
@MongoIntegrationTest
@ParameterizedClass(name = "exactPathMaxCited={0}")
@ValueSource(ints = {0, 200_000}) // 0 forces the stamp scan, 200_000 the exact path
class DashboardAggregationsMongoTest {

    @Parameter int exactPathMaxCited;

    private static final String ORG = "org-1";
    private static final LocalDate BASE = LocalDate.of(2099, 1, 1);

    @Autowired private MongoTemplate mongoTemplate;
    @Autowired private AiUsageAllocationMongoRepository allocationMongo;
    @Autowired private AiUsageMongoRepository usageMongo;
    private int seq;
    private AiUsageAllocationRepositoryImpl repository;
    private AiUsageRepositoryImpl usages;

    @BeforeEach
    void setUp() {
        repository = new AiUsageAllocationRepositoryImpl(allocationMongo, new AiUsageAllocationDocumentMapper(),
                mongoTemplate);
        repository.exactPathMaxCited = exactPathMaxCited;
        usages = new AiUsageRepositoryImpl(usageMongo, new AiUsageDocumentMapper(), mongoTemplate);
        mongoTemplate.remove(new Query(), "ai_usages");
        mongoTemplate.remove(new Query(), "ai_usage_allocations");
    }

    private AiUsageDocument usage(String org, String id, int dayOffset, String model, long in, long out, int interactions,
                                  String cost, boolean active) {
        return mongoTemplate.insert(AiUsageDocument.builder().id(id).organizationId(org).userId("u1").machineId("m" + (++seq))
                .day(BASE.plusDays(dayOffset)).model(model).inputTokens(in).outputTokens(out)
                .interactions(interactions).costUsd(cost == null ? null : new BigDecimal(cost)).active(active).build());
    }

    private AiUsageAllocationDocument allocation(String org, int dayOffset, AiUsageAllocation.Status status,
                                                 boolean active, List<String> usageIds, String... workItems) {
        List<AllocationSplitDocument> splits = new ArrayList<>();
        for (String workItem : workItems) {
            splits.add(new AllocationSplitDocument(workItem, null, new BigDecimal("50")));
        }
        return mongoTemplate.insert(AiUsageAllocationDocument.builder().organizationId(org).userId("u1")
                .day(BASE.plusDays(dayOffset)).aiUsageIds(usageIds).splits(splits).status(status).active(active).build());
    }

    private static AiUsageAllocation.Status validated() {
        return AiUsageAllocation.Status.VALIDATED;
    }

    @Test
    void should_matchTheJavaLoop_when_dataIsRandomAndWorkItemsSpanSeveralChunks() {
        Random random = new Random(7_654_321L);
        String[] models = {"opus", "sonnet", null};
        List<String> org1 = new ArrayList<>();
        List<String> org2 = new ArrayList<>();
        for (int i = 0; i < 300; i++) {
            boolean other = random.nextInt(4) == 0;
            String id = random.nextInt(5) == 0 ? "legacy-" + i : null; // string id (not hex) vs generated ObjectId
            String cost = random.nextInt(5) == 0 ? null : (random.nextInt(500) / 100.0 + 0.003 * random.nextInt(20)) + "";
            AiUsageDocument saved = usage(other ? "org-2" : ORG, id, random.nextInt(30), models[random.nextInt(3)],
                    random.nextInt(4000), random.nextInt(1500), random.nextInt(7), cost, random.nextInt(8) != 0);
            (other ? org2 : org1).add(saved.getId());
        }
        AiUsageAllocation.Status[] statuses = {validated(), validated(), validated(), AiUsageAllocation.Status.PENDING_VALIDATION};
        for (int i = 0; i < 200; i++) {
            List<String> ids = new ArrayList<>();
            int count = random.nextInt(5); // 0 gives an empty allocation
            for (int k = 0; k < count; k++) {
                int pick = random.nextInt(20);
                if (pick == 0) ids.add("ghost-" + random.nextInt(9));
                else if (pick == 1) ids.add("ffffffffffffffffffffffff");
                else if (pick <= 3) ids.add(org2.get(random.nextInt(org2.size())));
                else ids.add(org1.get(random.nextInt(org1.size())));
            }
            if (!ids.isEmpty() && random.nextInt(6) == 0) ids.add(ids.get(0)); // repeated id
            String first = "w" + random.nextInt(2500);
            String second = "w" + random.nextInt(2500);
            allocation(random.nextInt(10) == 0 ? "org-2" : ORG, random.nextInt(30), statuses[random.nextInt(4)],
                    random.nextInt(10) != 0, ids, random.nextInt(3) == 0 ? new String[]{first} : new String[]{first, second});
        }
        List<String> workItems = IntStream.range(0, 2500).mapToObj(i -> "w" + i).toList(); // 3 chunks
        LocalDate from = BASE.plusDays(4);
        LocalDate to = BASE.plusDays(22);

        for (LocalDate[] window : new LocalDate[][]{{from, to}, {null, null}}) {
            List<AiUsageAllocation> reference = window[0] == null
                    ? repository.findValidatedByOrganizationIdAndWorkItemIdIn(ORG, workItems)
                    : repository.findValidatedByOrganizationIdAndWorkItemIdInAndDayBetween(ORG, workItems, window[0], window[1]);
            assertThat(reference).hasSizeGreaterThan(40);
            Map<String, AIUsage> found = new HashMap<>();
            usages.findCostFieldsByOrganizationIdAndIdIn(ORG, reference.stream()
                    .flatMap(a -> a.getAiUsageIds().stream()).distinct().toList()).forEach(u -> found.put(u.getId(), u));

            Map<String, AllocationCoverage> coverage = new HashMap<>();
            repository.summarizeValidatedCoverage(ORG, workItems, window[0], window[1])
                    .forEach(c -> assertThat(coverage.put(c.id(), c)).as("one row per allocation").isNull());
            assertThat(coverage).hasSize(reference.size());
            Map<String, List<AllocationCoverageDetail>> detail = new HashMap<>();
            repository.summarizeValidatedCoverageByDayAndModel(ORG, workItems, window[0], window[1])
                    .forEach(d -> detail.computeIfAbsent(d.allocationId(), k -> new ArrayList<>()).add(d));

            record Key(LocalDate day, String model) {}
            for (AiUsageAllocation allocation : reference) {
                BigDecimal cost = BigDecimal.ZERO;
                long tokens = 0;
                Map<Key, long[]> perKey = new HashMap<>();
                Map<Key, BigDecimal> perKeyCost = new HashMap<>();
                for (String id : allocation.getAiUsageIds()) {
                    AIUsage usage = found.get(id);
                    if (usage == null) continue;
                    BigDecimal c = usage.getCostUsd() == null ? BigDecimal.ZERO : usage.getCostUsd();
                    cost = cost.add(c);
                    tokens += usage.getInputTokens() + usage.getOutputTokens();
                    Key key = new Key(usage.getDay(), usage.getModel());
                    long[] sums = perKey.computeIfAbsent(key, k -> new long[3]);
                    sums[0] += usage.getInputTokens();
                    sums[1] += usage.getOutputTokens();
                    sums[2] += usage.getInteractions();
                    perKeyCost.merge(key, c, BigDecimal::add);
                }
                AllocationCoverage row = coverage.get(allocation.getId());
                assertThat(row).as("allocation " + allocation.getId()).isNotNull();
                assertThat(row.userId()).isEqualTo(allocation.getUserId());
                assertThat(row.day()).isEqualTo(allocation.getDay());
                assertThat(row.costUsd()).isEqualByComparingTo(cost);
                assertThat(row.tokens()).isEqualTo(tokens);
                assertThat(row.splits()).extracting(AllocationSplit::getWorkItemId)
                        .containsExactlyElementsOf(allocation.getSplits().stream().map(AllocationSplit::getWorkItemId).toList());
                assertThat(row.splits()).extracting(AllocationSplit::getPercentage)
                        .usingElementComparator(BigDecimal::compareTo)
                        .containsExactlyElementsOf(allocation.getSplits().stream().map(AllocationSplit::getPercentage).toList());

                List<AllocationCoverageDetail> rows = detail.getOrDefault(allocation.getId(), List.of());
                assertThat(rows).hasSize(perKey.size());
                for (AllocationCoverageDetail d : rows) {
                    Key key = new Key(d.day(), d.model());
                    assertThat(perKey).containsKey(key);
                    assertThat(d.inputTokens()).isEqualTo(perKey.get(key)[0]);
                    assertThat(d.outputTokens()).isEqualTo(perKey.get(key)[1]);
                    assertThat(d.interactions()).isEqualTo(perKey.get(key)[2]);
                    assertThat(d.costUsd()).isEqualByComparingTo(perKeyCost.get(key));
                    assertThat(d.userId()).isEqualTo(allocation.getUserId());
                }
            }
            assertThat(detail.keySet()).isSubsetOf(coverage.keySet());
        }
    }

    @Test
    void should_countByHand_when_edgesCombine() {
        AiUsageDocument a = usage(ORG, null, 1, "opus", 10, 5, 2, "1.50", true);
        AiUsageDocument b = usage(ORG, "legacy-b", 2, null, 20, 0, 1, null, true);       // string id, null cost and model
        AiUsageDocument inactive = usage(ORG, null, 1, "opus", 99, 99, 9, "9", false);
        AiUsageDocument foreign = usage("org-2", null, 1, "opus", 99, 99, 9, "9", true);  // other organization

        // repeated id counts twice; the foreign, the inactive and the unknown ids are ignored
        AiUsageAllocationDocument counted = allocation(ORG, 1, validated(), true,
                List.of(a.getId(), a.getId(), b.getId(), inactive.getId(), foreign.getId(), "ghost"), "w1", "w-out");
        AiUsageAllocationDocument empty = allocation(ORG, 1, validated(), true, List.of(), "w1");
        AiUsageAllocationDocument nullIds = allocation(ORG, 1, validated(), true, null, "w1");
        allocation(ORG, 1, AiUsageAllocation.Status.PENDING_VALIDATION, true, List.of(a.getId()), "w1");
        allocation(ORG, 1, validated(), false, List.of(a.getId()), "w1");
        allocation("org-2", 1, validated(), true, List.of(a.getId()), "w1");
        allocation(ORG, 1, validated(), true, List.of(a.getId()), "w-other");
        allocation(ORG, 9, validated(), true, List.of(a.getId()), "w1");                 // outside the window

        List<AllocationCoverage> rows = repository.summarizeValidatedCoverage(ORG, List.of("w1"),
                BASE.plusDays(1), BASE.plusDays(2));

        assertThat(rows).extracting(AllocationCoverage::id)
                .containsExactlyInAnyOrder(counted.getId(), empty.getId(), nullIds.getId());
        AllocationCoverage row = rows.stream().filter(r -> r.id().equals(counted.getId())).findFirst().orElseThrow();
        assertThat(row.costUsd()).isEqualByComparingTo("3.00");
        assertThat(row.tokens()).isEqualTo(15 + 15 + 20);
        assertThat(row.splits()).extracting(AllocationSplit::getWorkItemId).containsExactly("w1", "w-out");
        for (String id : List.of(empty.getId(), nullIds.getId())) {
            AllocationCoverage zero = rows.stream().filter(r -> r.id().equals(id)).findFirst().orElseThrow();
            assertThat(zero.costUsd()).isEqualByComparingTo("0");
            assertThat(zero.tokens()).isZero();
        }

        List<AllocationCoverageDetail> detail = repository.summarizeValidatedCoverageByDayAndModel(ORG, List.of("w1"),
                BASE.plusDays(1), BASE.plusDays(2));
        assertThat(detail).hasSize(2).allSatisfy(d -> assertThat(d.allocationId()).isEqualTo(counted.getId()));
        AllocationCoverageDetail opus = detail.stream().filter(d -> "opus".equals(d.model())).findFirst().orElseThrow();
        assertThat(opus.day()).isEqualTo(BASE.plusDays(1));
        assertThat(opus.inputTokens()).isEqualTo(20);
        assertThat(opus.outputTokens()).isEqualTo(10);
        assertThat(opus.interactions()).isEqualTo(4);
        assertThat(opus.costUsd()).isEqualByComparingTo("3.00");
        AllocationCoverageDetail noModel = detail.stream().filter(d -> d.model() == null).findFirst().orElseThrow();
        assertThat(noModel.day()).isEqualTo(BASE.plusDays(2));
        assertThat(noModel.costUsd()).isEqualByComparingTo("0");
    }

    @Test
    void should_includeBothWindowEdges_andOpenWindowWhenBoundsAreNull() {
        AiUsageDocument a = usage(ORG, null, 0, "opus", 1, 1, 1, "1", true);
        allocation(ORG, 3, validated(), true, List.of(a.getId()), "w1");
        allocation(ORG, 5, validated(), true, List.of(a.getId()), "w1");
        allocation(ORG, 4, validated(), true, List.of(a.getId()), "w1");
        allocation(ORG, 6, validated(), true, List.of(a.getId()), "w1");

        assertThat(repository.summarizeValidatedCoverage(ORG, List.of("w1"), BASE.plusDays(3), BASE.plusDays(5)))
                .hasSize(3);
        assertThat(repository.summarizeValidatedCoverage(ORG, List.of("w1"), null, null)).hasSize(4);
        assertThat(repository.summarizeValidatedCoverage(ORG, List.of(), null, null)).isEmpty();
        assertThat(repository.summarizeValidatedCoverageByDayAndModel(ORG, null, null, null)).isEmpty();
    }

    @Test
    void should_keepAnAllocationOnce_when_itSplitsToWorkItemsOfTwoChunks() {
        AiUsageDocument a = usage(ORG, null, 0, "opus", 1, 1, 1, "1", true);
        AiUsageAllocationDocument both = allocation(ORG, 1, validated(), true, List.of(a.getId()), "w5", "w1500");
        List<String> workItems = IntStream.range(0, 2500).mapToObj(i -> "w" + i).toList();

        assertThat(repository.summarizeValidatedCoverage(ORG, workItems, null, null)).singleElement()
                .satisfies(c -> assertThat(c.id()).isEqualTo(both.getId()));
        assertThat(repository.summarizeValidatedCoverageByDayAndModel(ORG, workItems, null, null)).singleElement()
                .satisfies(d -> assertThat(d.allocationId()).isEqualTo(both.getId()));
    }

    // --- PERF2 D7: coverage by the stamp; a list that does not match its stamped records goes to the $lookup ---

    private void stamp(AiUsageDocument usage, AiUsageAllocationDocument allocation) {
        mongoTemplate.updateFirst(Query.query(org.springframework.data.mongodb.core.query.Criteria.where("_id")
                        .is(usage.getId())),
                org.springframework.data.mongodb.core.query.Update.update("allocationId", allocation.getId()),
                AiUsageDocument.class);
    }

    private AiUsageAllocationDocument stamped(int dayOffset, List<AiUsageDocument> cited, List<AiUsageDocument> stampedOnes,
                                              String... workItems) {
        AiUsageAllocationDocument allocation = allocation(ORG, dayOffset, validated(), true,
                cited.stream().map(AiUsageDocument::getId).toList(), workItems);
        stampedOnes.forEach(u -> stamp(u, allocation));
        return allocation;
    }

    /** Both summaries equal the Java loop over the cited ids; {@code fallbacks} allocations took the $lookup. */
    private void assertLikeTheCitedList(List<String> workItems, long fallbacks) {
        List<AiUsageAllocation> reference = repository.findValidatedByOrganizationIdAndWorkItemIdIn(ORG, workItems);
        Map<String, AIUsage> found = new HashMap<>();
        usages.findCostFieldsByOrganizationIdAndIdIn(ORG, reference.stream()
                .flatMap(a -> a.getAiUsageIds().stream()).distinct().toList()).forEach(u -> found.put(u.getId(), u));

        if (exactPathMaxCited > 0) fallbacks = 0; // the exact path serves all of them, no check
        repository.fallbackAllocations.set(0);
        List<AllocationCoverage> coverage = repository.summarizeValidatedCoverage(ORG, workItems, null, null);
        assertThat(repository.fallbackAllocations.get()).as("fallback of the coverage").isEqualTo(fallbacks);
        repository.fallbackAllocations.set(0);
        List<AllocationCoverageDetail> detail = repository.summarizeValidatedCoverageByDayAndModel(ORG, workItems, null, null);
        assertThat(repository.fallbackAllocations.get()).as("fallback of the detail").isEqualTo(fallbacks);

        assertThat(coverage).extracting(AllocationCoverage::id)
                .containsExactlyInAnyOrderElementsOf(reference.stream().map(AiUsageAllocation::getId).toList());
        record Key(String allocation, LocalDate day, String model) {}
        Map<Key, long[]> expectedDetail = new HashMap<>();
        Map<Key, BigDecimal> expectedDetailCost = new HashMap<>();
        for (AiUsageAllocation allocation : reference) {
            BigDecimal cost = BigDecimal.ZERO;
            long tokens = 0;
            for (String id : allocation.getAiUsageIds()) {
                AIUsage usage = found.get(id);
                if (usage == null) continue;
                BigDecimal c = usage.getCostUsd() == null ? BigDecimal.ZERO : usage.getCostUsd();
                cost = cost.add(c);
                tokens += usage.getInputTokens() + usage.getOutputTokens();
                Key key = new Key(allocation.getId(), usage.getDay(), usage.getModel());
                long[] sums = expectedDetail.computeIfAbsent(key, k -> new long[3]);
                sums[0] += usage.getInputTokens();
                sums[1] += usage.getOutputTokens();
                sums[2] += usage.getInteractions();
                expectedDetailCost.merge(key, c, BigDecimal::add);
            }
            AllocationCoverage row = coverage.stream().filter(c -> c.id().equals(allocation.getId())).findFirst().orElseThrow();
            assertThat(row.costUsd()).isEqualByComparingTo(cost);
            assertThat(row.tokens()).isEqualTo(tokens);
            assertThat(row.userId()).isEqualTo(allocation.getUserId());
            assertThat(row.day()).isEqualTo(allocation.getDay());
        }
        assertThat(detail).hasSize(expectedDetail.size());
        for (AllocationCoverageDetail d : detail) {
            Key key = new Key(d.allocationId(), d.day(), d.model());
            assertThat(expectedDetail).containsKey(key);
            assertThat(d.inputTokens()).isEqualTo(expectedDetail.get(key)[0]);
            assertThat(d.outputTokens()).isEqualTo(expectedDetail.get(key)[1]);
            assertThat(d.interactions()).isEqualTo(expectedDetail.get(key)[2]);
            assertThat(d.costUsd()).isEqualByComparingTo(expectedDetailCost.get(key));
        }
    }

    @Test
    void should_notTakeTheLookup_when_theStampMatchesTheCitedList() {
        AiUsageDocument a = usage(ORG, null, 1, "opus", 10, 5, 2, "1.50", true);
        AiUsageDocument b = usage(ORG, "legacy-b", 2, null, 20, 0, 1, null, true);   // string id, null cost and model
        AiUsageDocument c = usage(ORG, null, 1, "opus", 7, 3, 1, "0.25", true);
        stamped(1, List.of(a, b, c), List.of(a, b, c), "w1");
        stamped(2, List.of(), List.of(), "w1");                                       // no ids

        assertLikeTheCitedList(List.of("w1"), 0);
        assertThat(repository.summarizeValidatedCoverage(ORG, List.of("w1"), null, null))
                .anySatisfy(r -> {
                    assertThat(r.tokens()).isEqualTo(15 + 20 + 10);
                    assertThat(r.costUsd()).isEqualByComparingTo("1.75");
                })
                .anySatisfy(r -> assertThat(r.tokens()).isZero());
    }

    @Test
    void should_takeTheLookup_when_aCitedIdIsRepeated() {
        AiUsageDocument a = usage(ORG, null, 1, "opus", 10, 5, 2, "1.50", true);
        stamped(1, List.of(a, a), List.of(a), "w1");
        assertLikeTheCitedList(List.of("w1"), 1);
    }

    @Test
    void should_takeTheLookup_when_aCitedRecordIsInactive() {
        AiUsageDocument a = usage(ORG, null, 1, "opus", 10, 5, 2, "1.50", true);
        AiUsageDocument inactive = usage(ORG, null, 1, "opus", 99, 99, 9, "9", false);
        stamped(1, List.of(a, inactive), List.of(a, inactive), "w1");
        assertLikeTheCitedList(List.of("w1"), 1);
    }

    @Test
    void should_takeTheLookup_when_aCitedRecordBelongsToAnotherOrganization() {
        AiUsageDocument a = usage(ORG, null, 1, "opus", 10, 5, 2, "1.50", true);
        AiUsageDocument foreign = usage("org-2", null, 1, "opus", 99, 99, 9, "9", true);
        stamped(1, List.of(a, foreign), List.of(a, foreign), "w1");
        assertLikeTheCitedList(List.of("w1"), 1);
    }

    @Test
    void should_takeTheLookup_when_aCitedIdDoesNotExist() {
        AiUsageDocument a = usage(ORG, null, 1, "opus", 10, 5, 2, "1.50", true);
        AiUsageAllocationDocument allocation = allocation(ORG, 1, validated(), true,
                List.of(a.getId(), "ghost", "ffffffffffffffffffffffff"), "w1");
        stamp(a, allocation);
        assertLikeTheCitedList(List.of("w1"), 1);
    }

    @Test
    void should_takeTheLookup_when_aRecordIsStampedButNotCited() {
        AiUsageDocument a = usage(ORG, null, 1, "opus", 10, 5, 2, "1.50", true);
        AiUsageDocument extra = usage(ORG, null, 1, "opus", 99, 99, 9, "9", true);
        stamped(1, List.of(a), List.of(a, extra), "w1");
        assertLikeTheCitedList(List.of("w1"), 1);
    }

    @Test
    void should_takeTheLookup_when_aRecordIsCitedButNotStamped() {
        AiUsageDocument a = usage(ORG, null, 1, "opus", 10, 5, 2, "1.50", true);
        AiUsageDocument b = usage(ORG, null, 1, "opus", 20, 5, 2, "2", true);
        stamped(1, List.of(a, b), List.of(a), "w1");
        assertLikeTheCitedList(List.of("w1"), 1);
    }

    @Test
    void should_takeTheLookup_when_aCitedRecordIsOutsideTheDaysOfTheBatch() {
        AiUsageDocument a = usage(ORG, null, 1, "opus", 10, 5, 2, "1.50", true);
        AiUsageDocument far = usage(ORG, null, 20, "opus", 20, 5, 2, "2", true);
        stamped(1, List.of(a, far), List.of(a, far), "w1");
        stamped(2, List.of(), List.of(), "w1");
        assertLikeTheCitedList(List.of("w1"), 1);
    }

    @Test
    void should_takeTheLookup_when_anAllocationWithoutIdsHasStampedRecords() {
        AiUsageDocument a = usage(ORG, null, 1, "opus", 10, 5, 2, "1.50", true);
        AiUsageAllocationDocument nullIds = allocation(ORG, 1, validated(), true, null, "w1");
        stamp(a, nullIds);
        AiUsageDocument b = usage(ORG, null, 1, "opus", 20, 5, 2, "2", true);
        stamped(1, List.of(b), List.of(b), "w1"); // cites a record, so the stamp scan runs
        assertLikeTheCitedList(List.of("w1"), 1);
    }

    @Test
    void should_ignoreARecordOfAnotherOrganization_withTheSameAllocationIdAndUser() {
        AiUsageDocument a = usage(ORG, null, 1, "opus", 10, 5, 2, "1.50", true);
        AiUsageDocument foreign = usage("org-2", null, 1, "opus", 99, 99, 9, "9", true);
        stamped(1, List.of(a), List.of(a, foreign), "w1");

        assertLikeTheCitedList(List.of("w1"), 0);
        assertThat(repository.summarizeValidatedCoverage(ORG, List.of("w1"), null, null))
                .singleElement().satisfies(r -> assertThat(r.tokens()).isEqualTo(15));
    }

    @Test
    void should_mixConsistentAndInconsistentAllocationsInTheSameBatch() {
        AiUsageDocument a = usage(ORG, null, 1, "opus", 10, 5, 2, "1.50", true);
        AiUsageDocument b = usage(ORG, null, 2, "sonnet", 20, 5, 2, "2", true);
        AiUsageDocument c = usage(ORG, null, 2, "sonnet", 30, 5, 2, "3", true);
        stamped(1, List.of(a), List.of(a), "w1");
        stamped(2, List.of(b, b), List.of(b), "w1");
        stamped(2, List.of(c), List.of(c), "w2");

        assertLikeTheCitedList(List.of("w1", "w2"), 1);
    }

    @Test
    void should_keepTheFirstChunk_when_anInconsistentAllocationIsInTwoChunks() {
        AiUsageDocument a = usage(ORG, null, 1, "opus", 10, 5, 2, "1.50", true);
        AiUsageDocument b = usage(ORG, null, 1, "opus", 20, 5, 2, "2", true);
        AiUsageAllocationDocument both = stamped(1, List.of(a, a), List.of(a), "w5", "w1500");
        stamped(1, List.of(b), List.of(b), "w1500");
        List<String> workItems = IntStream.range(0, 2500).mapToObj(i -> "w" + i).toList();

        assertThat(repository.summarizeValidatedCoverage(ORG, workItems, null, null))
                .filteredOn(r -> r.id().equals(both.getId())).singleElement()
                .satisfies(r -> assertThat(r.tokens()).isEqualTo(30));
        assertThat(repository.summarizeValidatedCoverageByDayAndModel(ORG, workItems, null, null))
                .filteredOn(d -> d.allocationId().equals(both.getId())).singleElement()
                .satisfies(d -> assertThat(d.inputTokens()).isEqualTo(20));
    }

    @Test
    void should_signTheCitedListWithLongs() {
        AiUsageDocument a = usage(ORG, null, 1, "opus", 10, 5, 2, "1.50", true);
        stamped(1, List.of(a, a, a), List.of(a), "w1");

        List<org.bson.Document> rows = mongoTemplate.getCollection("ai_usage_allocations")
                .aggregate(repository.allocationSignaturePipeline(ORG, List.of("w1"), null, null))
                .into(new ArrayList<>());

        assertThat(rows).singleElement().satisfies(row -> {
            org.bson.Document sig = row.get("sig", org.bson.Document.class);
            assertThat(sig.get("n")).isEqualTo(3L);
            assertThat(sig.get("h1")).isInstanceOf(Long.class);
            assertThat(sig.get("h2")).isInstanceOf(Long.class);
            assertThat(sig.getLong("h1")).isNotZero();
        });
    }

    @Test
    void should_matchTheCitedList_when_consistentAndInconsistentAllocationsAreInDifferentChunks() {
        AiUsageDocument a = usage(ORG, null, 1, "opus", 10, 5, 2, "1.50", true);
        AiUsageDocument b = usage(ORG, null, 2, "sonnet", 20, 5, 2, "2", true);
        AiUsageDocument c = usage(ORG, null, 3, "opus", 30, 5, 2, "3", true);
        AiUsageDocument d = usage(ORG, null, 3, "opus", 40, 5, 2, "4", true);
        stamped(1, List.of(a), List.of(a), "w5");                       // chunk 1, consistent
        stamped(2, List.of(b, b), List.of(b), "w1200");                 // chunk 2, inconsistent
        stamped(3, List.of(c), List.of(c), "w2400");                    // chunk 3, consistent
        stamped(3, List.of(d, d), List.of(d), "w7", "w2300");           // two chunks, inconsistent
        List<String> workItems = IntStream.range(0, 2500).mapToObj(i -> "w" + i).toList();

        assertLikeTheCitedList(workItems, 2);
    }

    @Test
    void should_matchAnUppercaseHexId_likeTheConvertOfTheOldPipeline() {
        AiUsageDocument a = usage(ORG, null, 1, "opus", 10, 5, 2, "1.50", true);
        AiUsageAllocationDocument allocation = allocation(ORG, 1, validated(), true,
                List.of(a.getId().toUpperCase()), "w1");
        stamp(a, allocation);

        repository.fallbackAllocations.set(0);
        assertThat(repository.summarizeValidatedCoverage(ORG, List.of("w1"), null, null)).singleElement()
                .satisfies(r -> {
                    assertThat(r.tokens()).isEqualTo(15);
                    assertThat(r.costUsd()).isEqualByComparingTo("1.50");
                });
        assertThat(repository.summarizeValidatedCoverageByDayAndModel(ORG, List.of("w1"), null, null))
                .singleElement().satisfies(d -> assertThat(d.inputTokens()).isEqualTo(10));
        assertThat(repository.fallbackAllocations.get()).isZero();
    }

    @Test
    void should_scanTheStampOnlyAboveTheLimitOfCitedRecords() {
        AiUsageDocument a = usage(ORG, null, 1, "opus", 10, 5, 2, "1.50", true);
        AiUsageDocument b = usage(ORG, null, 1, "opus", 20, 5, 2, "2", true);
        stamped(1, List.of(a, b), List.of(a, b), "w1");

        repository.exactPathMaxCited = 2; // two cited records: not above
        repository.summarizeValidatedCoverage(ORG, List.of("w1"), null, null);
        repository.summarizeValidatedCoverageByDayAndModel(ORG, List.of("w1"), null, null);
        assertThat(repository.stampScans.get()).isZero();

        repository.exactPathMaxCited = 1;
        repository.summarizeValidatedCoverage(ORG, List.of("w1"), null, null);
        assertThat(repository.stampScans.get()).isEqualTo(1);
    }
}
