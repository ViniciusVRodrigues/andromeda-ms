package andromeda.sei.ms.feature.aiusage.infrastructure.persistence;

import andromeda.sei.ms.feature.aiusage.domain.AIUsage;
import andromeda.sei.ms.shared.testsupport.MongoIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** atribuicao-a-prova-de-esquecimento 1.2: daily token totals that feed the spike baseline. */
@MongoIntegrationTest
class AgentUsageBaselineMongoTest {

    private static final Instant NOW = Instant.parse("2099-01-10T10:00:00Z");
    private static final LocalDate DAY = LocalDate.of(2099, 1, 10);

    @Autowired private MongoTemplate mongoTemplate;
    private AgentUsageBaselineImpl baseline;

    @BeforeEach
    void setUp() {
        baseline = new AgentUsageBaselineImpl(mongoTemplate);
        mongoTemplate.remove(new Query(), "ai_usages");
    }

    private void save(String org, String user, LocalDate day, boolean agent, boolean active, long in, long out) {
        AIUsage u = new AIUsage(null, org, user, null, "ds", day, "m", "ws" + Math.random(), "github.com/a/b",
                "dev-1", "NOTE", in, out, 1, "claude-code", null, null, NOW, active, null, NOW, NOW);
        if (agent) {
            u.setSource("AGENT");
            u.setAgentKey("k" + Math.random());
            u.setDeviceId("dev-1");
            u.setBucketStart(Instant.parse("2099-01-10T09:00:00Z"));
        }
        mongoTemplate.insert(new AiUsageDocumentMapper().toDocument(u));
    }

    @Test
    void should_sumInputPlusOutputPerDay_onlyOfActiveAgentRowsOfThePersonInTheRange() {
        save("org-1", "user-1", DAY, true, true, 10, 5);
        save("org-1", "user-1", DAY, true, true, 20, 5);
        save("org-1", "user-1", DAY.minusDays(1), true, true, 100, 0);
        save("org-1", "user-1", DAY, false, true, 999, 999);
        save("org-1", "user-1", DAY, true, false, 999, 999);
        save("org-1", "user-2", DAY, true, true, 999, 999);
        save("org-2", "user-1", DAY, true, true, 999, 999);
        save("org-1", "user-1", DAY.minusDays(40), true, true, 999, 999);

        Map<LocalDate, Long> totals = baseline.dailyTokenTotals("org-1", "user-1", DAY.minusDays(28), DAY);

        assertThat(totals).containsOnly(Map.entry(DAY, 40L), Map.entry(DAY.minusDays(1), 100L));
    }

    @Test
    void should_returnEmpty_forAnInvertedRangeOrNoData() {
        assertThat(baseline.dailyTokenTotals("org-1", "user-1", DAY, DAY.minusDays(1))).isEmpty();
        assertThat(baseline.dailyTokenTotals("org-1", "user-1", DAY.minusDays(28), DAY)).isEmpty();
    }
}
