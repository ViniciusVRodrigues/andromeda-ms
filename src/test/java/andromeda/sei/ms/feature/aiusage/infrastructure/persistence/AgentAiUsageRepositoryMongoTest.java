package andromeda.sei.ms.feature.aiusage.infrastructure.persistence;

import andromeda.sei.ms.feature.agent.domain.AgentUploadBatch;
import andromeda.sei.ms.feature.agent.domain.UsageUploadResult;
import andromeda.sei.ms.feature.agent.infrastructure.persistence.AgentUploadBatchRepositoryImpl;
import andromeda.sei.ms.feature.aiusage.domain.AIUsage;
import andromeda.sei.ms.shared.testsupport.MongoIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** agente-endpoint-v2 3.1/3.2: unique key per device, raise-to-max, AIEF untouched, batch replay with TTL. */
@MongoIntegrationTest
class AgentAiUsageRepositoryMongoTest {

    private static final Instant NOW = Instant.parse("2099-01-01T10:00:00Z");
    private static final LocalDate DAY = LocalDate.of(2099, 1, 1);

    @Autowired private MongoTemplate mongoTemplate;
    @Autowired private AiUsageMongoRepository aiUsageMongo;
    private AgentAiUsageRepositoryImpl agent;
    private AiUsageRepositoryImpl aief;
    private AgentUploadBatchRepositoryImpl batches;

    @BeforeEach
    void setUp() {
        AiUsageDocumentMapper mapper = new AiUsageDocumentMapper();
        agent = new AgentAiUsageRepositoryImpl(mongoTemplate, mapper);
        aief = new AiUsageRepositoryImpl(aiUsageMongo, mapper, mongoTemplate);
        batches = new AgentUploadBatchRepositoryImpl(mongoTemplate);
        mongoTemplate.remove(new Query(), "ai_usages");
        mongoTemplate.remove(new Query(), "agent_upload_batches");
    }

    private AIUsage agentRow(String key, String device, long output) {
        AIUsage u = new AIUsage(null, "org-1", "user-1", null, "agent", DAY, "claude-sonnet-5-5", "agent:" + key,
                "github.com/a/b", device, "NOTE", 10, output, 1, "claude-code", null, null, NOW, true, null, NOW, NOW);
        u.setAgentKey(key);
        u.setDeviceId(device);
        u.setBucketStart(Instant.parse("2099-01-01T09:00:00Z"));
        return u;
    }

    @Test
    void should_ignoreTheSecondInsert_when_theKeyIsAlreadyStoredForTheDevice() {
        assertThat(agent.insert(agentRow("k1", "dev-1", 100))).isPresent();
        assertThat(agent.insert(agentRow("k1", "dev-1", 999))).isEmpty();
        assertThat(agent.insert(agentRow("k1", "dev-2", 50))).isPresent(); // same key, other device
        assertThat(agent.findByKeys("org-1", "dev-1", List.of("k1")).get("k1").getOutputTokens()).isEqualTo(100);
    }

    @Test
    void should_raiseToTheMaximumPerField_neverLower() {
        AIUsage stored = agent.insert(agentRow("k1", "dev-1", 3400)).orElseThrow();

        agent.raiseToMax("org-1", stored.getId(), agentRow("k1", "dev-1", 5100));
        agent.raiseToMax("org-1", stored.getId(), agentRow("k1", "dev-1", 3400));

        AIUsage after = agent.findByKeys("org-1", "dev-1", List.of("k1")).get("k1");
        assertThat(after.getOutputTokens()).isEqualTo(5100);
    }

    @Test
    void should_doNothing_when_theOrganizationDoesNotMatch() {
        AIUsage stored = agent.insert(agentRow("k1", "dev-1", 10)).orElseThrow();
        agent.raiseToMax("other-org", stored.getId(), agentRow("k1", "dev-1", 5000));
        assertThat(agent.findByKeys("org-1", "dev-1", List.of("k1")).get("k1").getOutputTokens()).isEqualTo(10);
    }

    @Test
    void should_keepAiefRowsWithoutAgentKeyOutOfTheUniqueIndex_andUpsertThemAsBefore() {
        AIUsage aiefRow = new AIUsage(null, "org-1", "user-1", null, "ds-1", DAY, "claude", "ws", null, "m", null,
                10, 5, 1, "vscode", null, null, NOW, true, null, null, null);

        aief.upsert(aiefRow);
        aief.upsert(aiefRow);
        AIUsage other = new AIUsage(null, "org-1", "user-1", null, "ds-1", DAY, "claude", "ws2", null, "m", null,
                10, 5, 1, "vscode", null, null, NOW, true, null, null, null);
        aief.upsert(other);

        assertThat(mongoTemplate.count(new Query(), "ai_usages")).isEqualTo(2); // no collision between null keys
        assertThat(agent.findKeysByDeviceAndDay("org-1", "m", DAY, 10)).isEmpty();
    }

    @Test
    void should_countOnlyAgentRowsOfTheDevice_inReconciliation() {
        agent.insert(agentRow("k1", "dev-1", 1));
        agent.insert(agentRow("k2", "dev-1", 2));
        agent.insert(agentRow("k3", "dev-2", 3));
        aief.upsert(new AIUsage(null, "org-1", "user-1", null, "ds-1", DAY, "claude", "ws", null, "dev-1", null,
                10, 5, 1, "vscode", null, null, NOW, true, null, null, null));

        assertThat(agent.findCountersByDeviceAndDayBetween("org-1", "dev-1", DAY, DAY)).hasSize(2);
        assertThat(agent.findKeysByDeviceAndDay("org-1", "dev-1", DAY, 10)).containsExactly("k1", "k2");
    }

    @Test
    void should_declareTheUniqueIndex() {
        List<IndexInfo> indexes = mongoTemplate.indexOps("ai_usages").getIndexInfo();
        IndexInfo unique = indexes.stream().filter(i -> "uq_ai_usage_agent_key".equals(i.getName())).findFirst().orElseThrow();
        assertThat(unique.isUnique()).isTrue();
    }

    @Test
    void should_replayTheStoredBatch_andKeepTheFirstWhenSavedTwice() {
        UsageUploadResult first = new UsageUploadResult("b1", 1, 0, 0, List.of(new UsageUploadResult.RowResult(
                "k1", UsageUploadResult.Status.ACCEPTED, UsageUploadResult.Allocation.QUEUED, null, null, null, null, null)));
        batches.save(new AgentUploadBatch("org-1", "dev-1", "b1", first, NOW, NOW.plusSeconds(3600)));
        batches.save(new AgentUploadBatch("org-1", "dev-1", "b1",
                new UsageUploadResult("b1", 0, 0, 0, List.of()), NOW, NOW.plusSeconds(3600)));

        Optional<AgentUploadBatch> found = batches.find("org-1", "dev-1", "b1");

        assertThat(found).isPresent();
        assertThat(found.get().getResponse()).isEqualTo(first);
        assertThat(batches.find("org-1", "dev-2", "b1")).isEmpty();
        assertThat(mongoTemplate.count(new Query(), "agent_upload_batches")).isEqualTo(1);
    }

    @Test
    void should_declareTheTtlIndexOnExpiresAt() {
        IndexInfo ttl = mongoTemplate.indexOps("agent_upload_batches").getIndexInfo().stream()
                .filter(i -> "ttl_agent_upload_batch_expires".equals(i.getName())).findFirst().orElseThrow();
        assertThat(ttl.getExpireAfter()).isPresent();
    }
}
