package andromeda.sei.ms.feature.allocation.infrastructure.persistence;

import andromeda.sei.ms.feature.aiusage.infrastructure.persistence.AiUsageDocument;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation;
import andromeda.sei.ms.feature.allocation.domain.AllocationDestination;
import andromeda.sei.ms.feature.allocation.domain.AllocationSplit;
import andromeda.sei.ms.shared.testsupport.MongoIntegrationTest;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.Parameter;
import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * uso-ia-fora-de-entrega P4: the destination read (validated and awaiting allocations of some users, with the
 * cost behind each and the kind/category of every split) and the index its match uses (explain).
 */
@MongoIntegrationTest
@ParameterizedClass(name = "exactPathMaxCited={0}")
@ValueSource(ints = {0, 200_000}) // 0 forces the stamp scan, 200_000 the exact path
class AllocationDestinationMongoTest {

    @Parameter int exactPathMaxCited;

    private static final String ORG = "org-1";
    private static final LocalDate BASE = LocalDate.of(2099, 1, 1);

    @Autowired private MongoTemplate mongoTemplate;
    @Autowired private AiUsageAllocationMongoRepository allocationMongo;
    private AiUsageAllocationRepositoryImpl repository;
    private int seq;

    @BeforeEach
    void setUp() {
        repository = new AiUsageAllocationRepositoryImpl(allocationMongo, new AiUsageAllocationDocumentMapper(),
                mongoTemplate);
        repository.exactPathMaxCited = exactPathMaxCited;
        mongoTemplate.remove(new Query(), "ai_usages");
        mongoTemplate.remove(new Query(), "ai_usage_allocations");
    }

    private AiUsageDocument usage(String org, String user, int dayOffset, String cost, boolean active) {
        return mongoTemplate.insert(AiUsageDocument.builder().organizationId(org).userId(user)
                .machineId("m" + (++seq)).day(BASE.plusDays(dayOffset)).model("claude").inputTokens(100)
                .outputTokens(50).interactions(1).costUsd(new BigDecimal(cost)).active(active).build());
    }

    private AiUsageAllocationDocument allocation(String org, String user, int dayOffset,
                                                 AiUsageAllocation.Status status, boolean active,
                                                 List<AiUsageDocument> cited, boolean stamp,
                                                 AllocationSplitDocument... splits) {
        AiUsageAllocationDocument saved = mongoTemplate.insert(AiUsageAllocationDocument.builder()
                .organizationId(org).userId(user).day(BASE.plusDays(dayOffset))
                .aiUsageIds(cited.stream().map(AiUsageDocument::getId).toList()).splits(List.of(splits))
                .status(status).active(active).build());
        if (stamp) {
            cited.forEach(u -> mongoTemplate.updateFirst(
                    Query.query(Criteria.where("_id").is(u.getId())),
                    Update.update("allocationId", saved.getId()),
                    AiUsageDocument.class));
        }
        return saved;
    }

    private static AllocationSplitDocument item(String workItemId, String pct) {
        return new AllocationSplitDocument(workItemId, null, new BigDecimal(pct));
    }

    private static AllocationSplitDocument outOfDelivery(String pct, AllocationSplit.Category category) {
        return AllocationSplitDocument.builder().kind(AllocationSplit.Kind.OUT_OF_DELIVERY).category(category)
                .percentage(new BigDecimal(pct)).build();
    }

    @Test
    void should_readValidatedAndAwaitingOnly_withCostAndSplitKindAndCategory() {
        AiUsageDocument u1 = usage(ORG, "u1", 1, "1.50", true);
        AiUsageDocument u2 = usage(ORG, "u1", 1, "2.50", true);
        AiUsageDocument u3 = usage(ORG, "u1", 2, "4.00", true);
        AiUsageDocument inactive = usage(ORG, "u1", 3, "9.00", false);
        AiUsageDocument pending = usage(ORG, "u1", 4, "8.00", true);
        AiUsageAllocationDocument validated = allocation(ORG, "u1", 1, AiUsageAllocation.Status.VALIDATED, true,
                List.of(u1, u2), true, item("wi-1", "0.6"), outOfDelivery("0.4", AllocationSplit.Category.STUDY));
        AiUsageAllocationDocument awaiting = allocation(ORG, "u1", 2, AiUsageAllocation.Status.AWAITING_APPROVAL,
                true, List.of(u3), true, outOfDelivery("1", AllocationSplit.Category.OTHER));
        allocation(ORG, "u1", 3, AiUsageAllocation.Status.VALIDATED, true, List.of(inactive), true, item("wi-1", "1"));
        allocation(ORG, "u1", 4, AiUsageAllocation.Status.PENDING_VALIDATION, true, List.of(pending), true,
                item("wi-1", "1"));
        // excluded: superseded (inactive), other organization, other user, outside the window
        allocation(ORG, "u1", 5, AiUsageAllocation.Status.VALIDATED, false, List.of(), false, item("wi-1", "1"));
        allocation("org-2", "u1", 5, AiUsageAllocation.Status.VALIDATED, true, List.of(), false, item("wi-1", "1"));
        allocation(ORG, "u2", 5, AiUsageAllocation.Status.VALIDATED, true, List.of(), false, item("wi-1", "1"));
        allocation(ORG, "u1", 40, AiUsageAllocation.Status.VALIDATED, true, List.of(), false, item("wi-1", "1"));

        List<AllocationDestination> rows = repository.summarizeDestinationCoverage(ORG, List.of("u1"),
                BASE, BASE.plusDays(10));

        Map<String, AllocationDestination> byId = rows.stream()
                .collect(Collectors.toMap(AllocationDestination::id, r -> r));
        // validated, awaiting, and the validated one whose only cited record is inactive (zero cost)
        assertThat(byId).hasSize(3).containsKeys(validated.getId(), awaiting.getId());
        AllocationDestination v = byId.get(validated.getId());
        assertThat(v.status()).isEqualTo(AiUsageAllocation.Status.VALIDATED);
        assertThat(v.userId()).isEqualTo("u1");
        assertThat(v.costUsd()).isEqualByComparingTo("4.00");
        assertThat(v.tokens()).isEqualTo(300);
        assertThat(v.splits()).extracting(AllocationSplit::getKind)
                .containsExactly(AllocationSplit.Kind.ITEM, AllocationSplit.Kind.OUT_OF_DELIVERY);
        assertThat(v.splits().get(1).getCategory()).isEqualTo(AllocationSplit.Category.STUDY);
        assertThat(v.splits().get(1).getPercentage()).isEqualByComparingTo("0.4");
        AllocationDestination w = byId.get(awaiting.getId());
        assertThat(w.status()).isEqualTo(AiUsageAllocation.Status.AWAITING_APPROVAL);
        assertThat(w.costUsd()).isEqualByComparingTo("4.00");
        assertThat(w.splits().get(0).getCategory()).isEqualTo(AllocationSplit.Category.OTHER);
        // the validated allocation whose only cited record is inactive still appears, with zero cost
        assertThat(rows).filteredOn(r -> r.costUsd().signum() == 0).hasSize(1);
    }

    @Test
    void should_includeBothWindowEdges_andIgnoreWithoutUsers() {
        AiUsageDocument u = usage(ORG, "u1", 0, "1.00", true);
        allocation(ORG, "u1", 0, AiUsageAllocation.Status.VALIDATED, true, List.of(u), true, item("wi-1", "1"));
        allocation(ORG, "u1", 10, AiUsageAllocation.Status.VALIDATED, true, List.of(), false, item("wi-1", "1"));

        assertThat(repository.summarizeDestinationCoverage(ORG, List.of("u1"), BASE, BASE.plusDays(10))).hasSize(2);
        assertThat(repository.summarizeDestinationCoverage(ORG, List.of("u1"), BASE.plusDays(1), BASE.plusDays(9)))
                .isEmpty();
        assertThat(repository.summarizeDestinationCoverage(ORG, List.of(), BASE, BASE.plusDays(10))).isEmpty();
    }

    /** explain: the match of the read is served by an index of ai_usage_allocations, never a collection scan. */
    @Test
    void should_useAnIndexOnTheAllocations_whenReadingTheDestination() {
        for (int i = 0; i < 30; i++) {
            allocation(ORG, "u" + (i % 5), i % 20, AiUsageAllocation.Status.VALIDATED, true, List.of(), false,
                    item("wi-1", "1"));
        }
        List<Document> pipeline = new ArrayList<>(repository.destinationSignaturePipeline(ORG,
                List.of("u1", "u2"), BASE, BASE.plusDays(10)));
        Document explain = mongoTemplate.executeCommand(new Document("explain", new Document("aggregate",
                mongoTemplate.getCollectionName(AiUsageAllocationDocument.class)).append("pipeline", pipeline)
                .append("cursor", new Document())).append("verbosity", "queryPlanner"));
        String plan = explain.toJson();
        System.out.println("EXPLAIN-DESTINATION " + plan);
        assertThat(plan).doesNotContain("COLLSCAN");
        assertThat(plan).contains("IXSCAN");
    }
}
