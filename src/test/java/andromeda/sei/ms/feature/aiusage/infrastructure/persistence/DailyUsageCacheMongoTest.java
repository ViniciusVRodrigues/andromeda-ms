package andromeda.sei.ms.feature.aiusage.infrastructure.persistence;

import andromeda.sei.ms.feature.aiusage.domain.AIUsage;
import andromeda.sei.ms.feature.aiusage.domain.DailyAiUsage;
import andromeda.sei.ms.shared.testsupport.MongoIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Daily sum with cache: an AGENT row with cache and an AIEF row without the fields add up per day. */
@MongoIntegrationTest
class DailyUsageCacheMongoTest {

    private static final LocalDate DAY = LocalDate.of(2099, 2, 1);
    private static final Instant NOW = Instant.parse("2099-02-01T10:00:00Z");

    @Autowired private MongoTemplate mongoTemplate;
    @Autowired private AiUsageMongoRepository mongoRepository;

    @BeforeEach
    void clean() {
        mongoTemplate.remove(new Query(), "ai_usages");
    }

    private void insert(boolean agent, long cacheRead, long cacheWrite, boolean dropCacheFields) {
        AIUsage u = new AIUsage(null, "org-d", "user-d", null, "ds", DAY, "m", "ws" + Math.random(), "w", "mc", "M",
                100, 50, 2, "claude-code", null, BigDecimal.ONE, NOW, true, null, NOW, NOW);
        u.setCacheReadTokens(cacheRead);
        u.setCacheWriteTokens(cacheWrite);
        if (agent) u.setSource("AGENT");
        String id = mongoTemplate.insert(new AiUsageDocumentMapper().toDocument(u)).getId();
        if (dropCacheFields) {
            mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(id)),
                    new Update().unset("cacheReadTokens").unset("cacheWriteTokens"), AiUsageDocument.class);
        }
    }

    @Test
    void should_sumCachePerDay_withAiefRowMissingTheFields() {
        insert(true, 3000, 400, false);
        insert(false, 0, 0, true); // AIEF: no cache fields in the document

        var repo = new AiUsageRepositoryImpl(mongoRepository, new AiUsageDocumentMapper(), mongoTemplate);
        List<DailyAiUsage> days = repo.findDailyUsageByUserIdAndDateRange("org-d", "user-d", DAY, DAY, null);

        assertThat(days).hasSize(1);
        assertThat(days.get(0).cacheReadTokens()).isEqualTo(3000L);
        assertThat(days.get(0).cacheWriteTokens()).isEqualTo(400L);
        assertThat(days.get(0).inputTokens()).isEqualTo(200L);
        assertThat(days.get(0).outputTokens()).isEqualTo(100L);
    }
}
