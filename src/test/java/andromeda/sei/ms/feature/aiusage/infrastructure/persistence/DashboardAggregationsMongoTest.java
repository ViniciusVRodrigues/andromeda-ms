package andromeda.sei.ms.feature.aiusage.infrastructure.persistence;

import andromeda.sei.ms.feature.aiusage.domain.AIUsage;
import andromeda.sei.ms.feature.aiusage.domain.AiUsageDayModel;
import andromeda.sei.ms.feature.aiusage.domain.AiUsageEditorTotal;
import andromeda.sei.ms.shared.testsupport.MongoIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/** perf2 P1: the per-(day, model) and per-editor sums computed in Mongo equal the sums done in Java today. */
@MongoIntegrationTest
class DashboardAggregationsMongoTest {

    private static final String ORG = "org-1";
    private static final LocalDate BASE = LocalDate.of(2099, 1, 1);

    @Autowired private MongoTemplate mongoTemplate;
    @Autowired private AiUsageMongoRepository aiUsageMongo;
    private int seq;
    private AiUsageRepositoryImpl repository;

    @BeforeEach
    void setUp() {
        repository = new AiUsageRepositoryImpl(aiUsageMongo, new AiUsageDocumentMapper(), mongoTemplate);
        mongoTemplate.remove(new Query(), "ai_usages");
    }

    private AiUsageDocument insert(String org, String user, int dayOffset, String model, String editor,
                                   long in, long out, int interactions, String cost, boolean active, Instant uploaded) {
        return mongoTemplate.insert(AiUsageDocument.builder().organizationId(org).userId(user).machineId("m" + (++seq))
                .day(BASE.plusDays(dayOffset)).model(model).editor(editor).inputTokens(in).outputTokens(out)
                .interactions(interactions).costUsd(cost == null ? null : new BigDecimal(cost)).active(active)
                .uploadedAt(uploaded).build());
    }

    @Test
    void should_matchTheJavaSums_when_dataIsRandomWithNullsInactiveAndOtherOrganization() {
        Random random = new Random(20991231L);
        String[] models = {"opus", "sonnet", "haiku", null};
        String[] editors = {"vscode", "cursor", null};
        for (int i = 0; i < 600; i++) {
            String org = random.nextInt(5) == 0 ? "org-2" : ORG;
            String cost = random.nextInt(6) == 0 ? null : (random.nextInt(900) / 100.0 + 0.001 * random.nextInt(50)) + "";
            insert(org, "u" + random.nextInt(8), random.nextInt(40), models[random.nextInt(models.length)],
                    editors[random.nextInt(editors.length)], random.nextInt(5000), random.nextInt(2000),
                    random.nextInt(9), cost, random.nextInt(8) != 0,
                    random.nextInt(4) == 0 ? null : Instant.parse("2099-01-01T00:00:00Z").plusSeconds(random.nextInt(100000)));
        }
        List<String> users = List.of("u0", "u1", "u2", "u3", "u4", "u5", "u6");
        LocalDate from = BASE.plusDays(5);
        LocalDate to = BASE.plusDays(30);

        List<AIUsage> rows = repository.findDashboardFieldsByOrganizationIdAndUserIdInAndDayBetween(ORG, users, from, to);
        assertThat(rows).hasSizeGreaterThan(100);

        record Key(LocalDate day, String model) {}
        Map<Key, long[]> expected = new HashMap<>();
        Map<Key, BigDecimal> expectedCost = new HashMap<>();
        Map<Key, Instant> expectedUpload = new HashMap<>();
        Map<String, Long> expectedEditors = new HashMap<>();
        for (AIUsage row : rows) {
            Key key = new Key(row.getDay(), row.getModel());
            long[] sums = expected.computeIfAbsent(key, k -> new long[3]);
            sums[0] += row.getInputTokens();
            sums[1] += row.getOutputTokens();
            sums[2] += row.getInteractions();
            expectedCost.merge(key, row.getCostUsd() == null ? BigDecimal.ZERO : row.getCostUsd(), BigDecimal::add);
            if (row.getUploadedAt() != null) expectedUpload.merge(key, row.getUploadedAt(),
                    (a, b) -> a.isAfter(b) ? a : b);
            expectedEditors.merge(row.getEditor() == null ? "<null>" : row.getEditor(), (long) row.getInteractions(),
                    Long::sum);
        }

        List<AiUsageDayModel> actual = repository.summarizeByDayAndModel(ORG, users, from, to);
        assertThat(actual).hasSize(expected.size());
        for (AiUsageDayModel row : actual) {
            Key key = new Key(row.day(), row.model());
            assertThat(expected).containsKey(key);
            assertThat(row.inputTokens()).isEqualTo(expected.get(key)[0]);
            assertThat(row.outputTokens()).isEqualTo(expected.get(key)[1]);
            assertThat(row.interactions()).isEqualTo(expected.get(key)[2]);
            assertThat(row.costUsd()).isEqualByComparingTo(expectedCost.get(key));
            assertThat(row.lastUploadedAt()).isEqualTo(expectedUpload.get(key));
        }

        Map<String, Long> actualEditors = new HashMap<>();
        for (AiUsageEditorTotal total : repository.summarizeInteractionsByEditor(ORG, users, from, to)) {
            actualEditors.put(Objects.toString(total.editor(), "<null>"), total.interactions());
        }
        assertThat(actualEditors).isEqualTo(expectedEditors);
    }

    @Test
    void should_sumByHand_when_edgesCombine() {
        Instant early = Instant.parse("2099-02-01T10:00:00Z");
        Instant late = Instant.parse("2099-02-01T11:00:00Z");
        insert(ORG, "u1", 3, "opus", "vscode", 10, 5, 2, "1.50", true, early);
        insert(ORG, "u2", 3, "opus", "cursor", 20, 1, 3, null, true, late);          // null cost counts as 0
        insert(ORG, "u1", 3, null, null, 7, 7, 1, "0.25", true, null);              // null model and editor
        insert(ORG, "u1", 3, "opus", "vscode", 999, 999, 99, "99", false, late);    // inactive: out
        insert("org-2", "u1", 3, "opus", "vscode", 999, 999, 99, "99", true, late); // other organization: out
        insert(ORG, "u9", 3, "opus", "vscode", 999, 999, 99, "99", true, late);     // user not asked: out
        insert(ORG, "u1", 2, "opus", "vscode", 999, 999, 99, "99", true, late);     // day before the window: out
        insert(ORG, "u1", 6, "opus", "vscode", 4, 4, 4, "4", true, null);           // last day, inclusive
        insert(ORG, "u1", 7, "opus", "vscode", 999, 999, 99, "99", true, late);     // day after the window: out

        List<AiUsageDayModel> rows = repository.summarizeByDayAndModel(ORG, List.of("u1", "u2"),
                BASE.plusDays(3), BASE.plusDays(6));

        assertThat(rows).hasSize(3);
        AiUsageDayModel opus = rows.stream().filter(r -> "opus".equals(r.model()) && r.day().equals(BASE.plusDays(3)))
                .findFirst().orElseThrow();
        assertThat(opus.inputTokens()).isEqualTo(30);
        assertThat(opus.outputTokens()).isEqualTo(6);
        assertThat(opus.interactions()).isEqualTo(5);
        assertThat(opus.costUsd()).isEqualByComparingTo("1.50");
        assertThat(opus.lastUploadedAt()).isEqualTo(late);
        AiUsageDayModel noModel = rows.stream().filter(r -> r.model() == null).findFirst().orElseThrow();
        assertThat(noModel.costUsd()).isEqualByComparingTo("0.25");
        assertThat(noModel.lastUploadedAt()).isNull();
        AiUsageDayModel lastDay = rows.stream().filter(r -> r.day().equals(BASE.plusDays(6))).findFirst().orElseThrow();
        assertThat(lastDay.interactions()).isEqualTo(4);

        Map<String, Long> editors = new HashMap<>();
        repository.summarizeInteractionsByEditor(ORG, List.of("u1", "u2"), BASE.plusDays(3), BASE.plusDays(6))
                .forEach(t -> editors.put(Objects.toString(t.editor(), "<null>"), t.interactions()));
        assertThat(editors).containsEntry("vscode", 6L).containsEntry("cursor", 3L).containsEntry("<null>", 1L)
                .hasSize(3);
    }

    @Test
    void should_returnEmpty_when_thereAreNoUsers() {
        insert(ORG, "u1", 3, "opus", "vscode", 1, 1, 1, "1", true, null);
        assertThat(repository.summarizeByDayAndModel(ORG, List.of(), BASE, BASE.plusDays(9))).isEmpty();
        assertThat(repository.summarizeByDayAndModel(ORG, null, BASE, BASE.plusDays(9))).isEmpty();
        assertThat(repository.summarizeInteractionsByEditor(ORG, List.of(), BASE, BASE.plusDays(9))).isEmpty();
    }

    @Test
    void should_keepUserAndTotalSummariesOnTheSameFilter() {
        insert(ORG, "u1", 3, "opus", "vscode", 10, 5, 2, "1.50", true, null);
        insert(ORG, "u1", 3, "opus", "vscode", 10, 5, 2, "9", false, null);
        insert("org-2", "u1", 3, "opus", "vscode", 10, 5, 2, "9", true, null);
        insert(ORG, "u1", 9, "opus", "vscode", 10, 5, 2, "9", true, null);

        assertThat(repository.summarizeByUserAndDay(ORG, List.of("u1"), BASE, BASE.plusDays(5))).singleElement()
                .satisfies(r -> assertThat(r.costUsd()).isEqualByComparingTo("1.50"));
        assertThat(repository.summarizeTotalsByUser(ORG, List.of("u1"), BASE, BASE.plusDays(5))).singleElement()
                .satisfies(r -> assertThat(r.inputTokens()).isEqualTo(10));
    }

    private static final String COVER = AiUsageDocument.DASHBOARD_COVER_INDEX;

    private static void collect(Object node, String key, List<String> into, List<Number> numbers) {
        if (node instanceof org.bson.Document d) {
            d.forEach((k, v) -> {
                if (k.equals("stage") && v instanceof String stage) into.add(stage);
                if (k.equals(key) && v instanceof Number n) numbers.add(n);
                collect(v, key, into, numbers);
            });
        } else if (node instanceof List<?> list) {
            list.forEach(v -> collect(v, key, into, numbers));
        }
    }

    @Test
    void should_coverTheDayAndModelSum_withoutReadingDocuments() {
        for (int i = 0; i < 40; i++) {
            insert(ORG, "u" + (i % 3), i % 10, i % 2 == 0 ? "opus" : "sonnet", "vscode", 10 + i, 5, 2, "1.25", true,
                    Instant.parse("2099-02-01T10:00:00Z"));
        }
        org.bson.Document command = new org.bson.Document("aggregate", "ai_usages")
                .append("pipeline", repository.dayAndModelPipeline(ORG, List.of("u0", "u1", "u2"), BASE, BASE.plusDays(30)))
                .append("cursor", new org.bson.Document()).append("hint", COVER);

        org.bson.Document explain = mongoTemplate.executeCommand(new org.bson.Document("explain", command)
                .append("verbosity", "executionStats"));

        List<String> stages = new java.util.ArrayList<>();
        List<Number> examined = new java.util.ArrayList<>();
        collect(explain, "totalDocsExamined", stages, examined);
        assertThat(stages).contains("PROJECTION_COVERED").doesNotContain("FETCH", "COLLSCAN");
        assertThat(examined).isNotEmpty().allSatisfy(n -> assertThat(n.longValue()).isZero());
    }

    @Test
    void should_returnTheSameSums_when_theCoverIndexIsMissing() {
        insert(ORG, "u1", 3, "opus", "vscode", 10, 5, 2, "1.50", true, null);
        insert(ORG, "u1", 3, "opus", "cursor", 20, 1, 3, null, true, null);
        List<AiUsageDayModel> withIndex = repository.summarizeByDayAndModel(ORG, List.of("u1"), BASE, BASE.plusDays(9));
        assertThat(withIndex).singleElement().satisfies(r -> assertThat(r.inputTokens()).isEqualTo(30));

        mongoTemplate.indexOps(AiUsageDocument.class).dropIndex(COVER);
        try {
            List<AiUsageDayModel> without = repository.summarizeByDayAndModel(ORG, List.of("u1"), BASE, BASE.plusDays(9));
            assertThat(without).isEqualTo(withIndex);
            assertThat(repository.summarizeInteractionsByEditor(ORG, List.of("u1"), BASE, BASE.plusDays(9))).hasSize(2);
            assertThat(repository.summarizeTotalsByUser(ORG, List.of("u1"), BASE, BASE.plusDays(9))).hasSize(1);
            assertThat(repository.summarizeByUserAndDay(ORG, List.of("u1"), BASE, BASE.plusDays(9))).hasSize(1);
        } finally {
            mongoTemplate.indexOps(AiUsageDocument.class).createIndex(new org.springframework.data.mongodb.core.index
                    .CompoundIndexDefinition(new org.bson.Document(coverKeys())).named(COVER));
        }
    }

    private static java.util.Map<String, Object> coverKeys() {
        java.util.Map<String, Object> keys = new java.util.LinkedHashMap<>();
        for (String field : List.of("organizationId", "userId", "day", "active", "model", "editor", "costUsd",
                "inputTokens", "outputTokens", "interactions", "allocationId", "uploadedAt", "_id")) {
            keys.put(field, 1);
        }
        return keys;
    }
}
