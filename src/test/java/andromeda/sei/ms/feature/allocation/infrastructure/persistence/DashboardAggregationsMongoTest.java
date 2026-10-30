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
class DashboardAggregationsMongoTest {

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
}
