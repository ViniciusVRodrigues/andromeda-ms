package andromeda.sei.ms.feature.aiusage.infrastructure.persistence;

import andromeda.sei.ms.feature.aiusage.domain.AIUsage;
import andromeda.sei.ms.shared.testsupport.MongoIntegrationTest;
import andromeda.sei.ms.shared.testsupport.QueryPlans;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * agt8: the agent lookups use the partial agent indexes. Without the repeated {@code $type} filter the
 * planner picks {@code {organizationId, userId, day}} and walks every row of the organization.
 */
@MongoIntegrationTest
class AgentAiUsageQueryPlanMongoTest {

    private static final Instant NOW = Instant.parse("2099-01-01T10:00:00Z");
    private static final LocalDate DAY = LocalDate.of(2099, 1, 1);

    @Autowired private MongoTemplate mongoTemplate;
    @Autowired private AiUsageMongoRepository aiUsageMongo;
    private AgentAiUsageRepositoryImpl agent;
    private AiUsageRepositoryImpl aief;

    @BeforeEach
    void setUp() {
        AiUsageDocumentMapper mapper = new AiUsageDocumentMapper();
        agent = new AgentAiUsageRepositoryImpl(mongoTemplate, mapper);
        aief = new AiUsageRepositoryImpl(aiUsageMongo, mapper, mongoTemplate);
        mongoTemplate.remove(new Query(), "ai_usages");
        for (int d = 0; d < 20; d++) {
            for (int k = 0; k < 10; k++) {
                agent.insert(agentRow("k" + d + "-" + k, "dev-" + (k % 2), DAY.minusDays(d)));
            }
            aief.upsert(new AIUsage(null, "org-1", "user-1", null, "ds-1", DAY.minusDays(d), "claude", "ws", null,
                    "m", null, 10, 5, 1, "vscode", null, null, NOW, true, null, null, null));
        }
    }

    private static AIUsage agentRow(String key, String device, LocalDate day) {
        AIUsage u = new AIUsage(null, "org-1", "user-1", null, "agent", day, "claude-sonnet-5-5", "agent:" + key,
                "github.com/a/b", device, "NOTE", 10, 1, 1, "claude-code", null, null, NOW, true, null, NOW, NOW);
        u.setAgentKey(key);
        u.setDeviceId(device);
        u.setBucketStart(day.atStartOfDay().toInstant(java.time.ZoneOffset.UTC));
        return u;
    }

    @Test
    void should_lookUpKeysByTheAgentUniqueIndex() {
        List<String> keys = new ArrayList<>(List.of("k0-0", "k0-2", "k3-4"));

        QueryPlans.Plan plan = QueryPlans.winning(mongoTemplate, AiUsageDocument.class,
                AgentAiUsageRepositoryImpl.keysQuery("org-1", "dev-0", keys));

        assertThat(plan.scansTheCollection()).isFalse();
        assertThat(plan.stages()).contains("IXSCAN");
        assertThat(plan.indexes()).containsOnly("uq_ai_usage_agent_key");
        assertThat(agent.findByKeys("org-1", "dev-0", keys)).containsOnlyKeys(keys);
    }

    @Test
    void should_readTheDeviceCountersByTheAgentDayIndex() {
        QueryPlans.Plan plan = QueryPlans.winning(mongoTemplate, AiUsageDocument.class,
                AgentAiUsageRepositoryImpl.countersQuery("org-1", "dev-0", DAY.minusDays(3), DAY));

        assertThat(plan.scansTheCollection()).isFalse();
        assertThat(plan.indexes()).containsOnly("idx_ai_usage_agent_day");
        assertThat(agent.findCountersByDeviceAndDayBetween("org-1", "dev-0", DAY.minusDays(3), DAY)).hasSize(20);
    }

    @Test
    void should_listTheKeysOfTheDayByTheAgentDayIndex() {
        QueryPlans.Plan plan = QueryPlans.winning(mongoTemplate, AiUsageDocument.class,
                AgentAiUsageRepositoryImpl.keysOfDayQuery("org-1", "dev-1", DAY, 3));

        assertThat(plan.scansTheCollection()).isFalse();
        assertThat(plan.indexes()).containsOnly("idx_ai_usage_agent_day");
        assertThat(agent.findKeysByDeviceAndDay("org-1", "dev-1", DAY, 3)).containsExactly("k0-1", "k0-3", "k0-5");
    }

    @Test
    void should_keepAiefRowsOutOfTheAgentLookups() {
        // AIEF row whose machineId equals a device id: never matched by the agent queries.
        assertThat(agent.findCountersByDeviceAndDayBetween("org-1", "m", DAY.minusDays(30), DAY)).isEmpty();
        assertThat(agent.findKeysByDeviceAndDay("org-1", "m", DAY, 10)).isEmpty();
        assertThat(mongoTemplate.count(new Query(), "ai_usages")).isEqualTo(220);
    }
}
