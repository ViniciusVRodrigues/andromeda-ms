package andromeda.sei.ms.feature.aiusage.infrastructure.persistence;

import andromeda.sei.ms.feature.aiusage.domain.AIUsage;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation;
import andromeda.sei.ms.feature.allocation.infrastructure.persistence.AiUsageAllocationDocument;
import andromeda.sei.ms.feature.allocation.infrastructure.persistence.AiUsageAllocationDocumentMapper;
import andromeda.sei.ms.feature.allocation.infrastructure.persistence.AiUsageAllocationMongoRepository;
import andromeda.sei.ms.feature.allocation.infrastructure.persistence.AiUsageAllocationRepositoryImpl;
import andromeda.sei.ms.shared.testsupport.MongoIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** agente-endpoint-v2 4.x: the queries behind the AIEF cutoff, the active task and the manual correction. */
@MongoIntegrationTest
class AgentUsageQueriesMongoTest {

    private static final Instant NOW = Instant.parse("2099-01-01T10:00:00Z");
    private static final LocalDate DAY = LocalDate.of(2099, 1, 1);
    private static final LocalDate BEFORE = LocalDate.of(2098, 12, 31);

    @Autowired private MongoTemplate mongoTemplate;
    @Autowired private AiUsageAllocationMongoRepository allocationMongo;
    private AgentAiUsageRepositoryImpl usages;
    private AiUsageAllocationRepositoryImpl allocations;

    @BeforeEach
    void setUp() {
        usages = new AgentAiUsageRepositoryImpl(mongoTemplate, new AiUsageDocumentMapper());
        allocations = new AiUsageAllocationRepositoryImpl(allocationMongo, new AiUsageAllocationDocumentMapper(), mongoTemplate);
        mongoTemplate.remove(new Query(), "ai_usages");
        mongoTemplate.remove(new Query(), "ai_usage_allocations");
    }

    private AIUsage save(String org, String user, LocalDate day, boolean agent, boolean active, String allocationId) {
        AIUsage u = new AIUsage(null, org, user, null, agent ? "agent" : "ds", day, "m", "ws" + Math.random(), "github.com/a/b",
                "dev-1", "NOTE", 1, 2, 1, "claude-code", null, null, NOW, active, null, NOW, NOW);
        u.setAllocationId(allocationId);
        if (agent) {
            u.setSource("AGENT");
            u.setAgentKey("k" + Math.random());
            u.setDeviceId("dev-1");
            u.setBucketStart(Instant.parse("2099-01-01T09:00:00Z"));
        }
        AiUsageDocument doc = new AiUsageDocumentMapper().toDocument(u);
        return new AiUsageDocumentMapper().toDomain(mongoTemplate.insert(doc));
    }

    private AIUsage reload(String id) {
        return new AiUsageDocumentMapper().toDomain(mongoTemplate.findById(id, AiUsageDocument.class));
    }

    private AIUsage aief(String org, String user, LocalDate day, String editor, boolean active, String allocationId) {
        AIUsage u = save(org, user, day, false, active, allocationId);
        mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(u.getId())),
                new org.springframework.data.mongodb.core.query.Update().set("editor", editor), AiUsageDocument.class);
        return u;
    }

    private String allocation(String org, AiUsageAllocation.Status status, boolean active) {
        return mongoTemplate.insert(AiUsageAllocationDocument.builder().organizationId(org).userId("user-1").day(DAY)
                .aiUsageIds(List.of()).splits(List.of()).status(status).assignedBy(AiUsageAllocation.AssignedBy.AUTO)
                .active(active).createdAt(NOW).updatedAt(NOW).build()).getId();
    }

    private static final List<String> CLAUDE = List.of("Claude Code CLI", "Claude (VS Code)", "Claude Code");

    @Test
    void should_supersedeOnlyTheActiveUnallocatedAiefOfTheToolOnTheDay() {
        AIUsage target = aief("org-1", "user-1", DAY, "Claude Code CLI", true, null);
        AIUsage target2 = aief("org-1", "user-1", DAY, "Claude (VS Code)", true, null);
        AIUsage copilot = aief("org-1", "user-1", DAY, "VS Code", true, null);
        AIUsage otherDay = aief("org-1", "user-1", DAY.plusDays(3), "Claude Code CLI", true, null);
        AIUsage earlier = aief("org-1", "user-1", BEFORE, "Claude Code CLI", true, null);
        AIUsage allocated = aief("org-1", "user-1", DAY, "Claude Code CLI", true, "alloc-1");
        AIUsage inactive = aief("org-1", "user-1", DAY, "Claude Code CLI", false, null);
        AIUsage agentRow = save("org-1", "user-1", DAY, true, true, null);
        AIUsage otherUser = aief("org-1", "user-2", DAY, "Claude Code CLI", true, null);
        AIUsage otherOrg = aief("org-2", "user-1", DAY, "Claude Code CLI", true, null);

        long n = usages.supersedeAief("org-1", "user-1", DAY, CLAUDE);

        assertThat(n).isEqualTo(2);
        assertThat(reload(target.getId()).isActive()).isFalse();
        assertThat(reload(target.getId()).getSupersededBy()).isEqualTo("AGENT");
        assertThat(reload(target2.getId()).isActive()).isFalse();
        assertThat(reload(copilot.getId()).isActive()).isTrue();
        assertThat(reload(otherDay.getId()).isActive()).isTrue();
        assertThat(reload(earlier.getId()).isActive()).isTrue();
        assertThat(reload(allocated.getId()).isActive()).isTrue();
        assertThat(reload(inactive.getId()).isActive()).isFalse();
        assertThat(reload(agentRow.getId()).isActive()).isTrue();
        assertThat(reload(otherUser.getId()).isActive()).isTrue();
        assertThat(reload(otherOrg.getId()).isActive()).isTrue();
    }

    @Test
    void should_checkIfAiefOfTheToolIsActiveOnTheDay() {
        aief("org-1", "user-1", DAY, "Claude Code CLI", true, null);
        aief("org-1", "user-1", BEFORE, "VS Code", true, null);
        save("org-1", "user-1", BEFORE, true, true, null);

        assertThat(usages.existsActiveAief("org-1", "user-1", DAY, CLAUDE)).isTrue();
        assertThat(usages.existsActiveAief("org-1", "user-1", DAY, List.of("Codex CLI"))).isFalse();
        assertThat(usages.existsActiveAief("org-1", "user-1", BEFORE, CLAUDE)).isFalse();
        assertThat(usages.existsActiveAief("org-1", "user-2", DAY, CLAUDE)).isFalse();
        assertThat(usages.existsActiveAief("org-2", "user-1", DAY, CLAUDE)).isFalse();
    }

    @Test
    void should_findAiefOfTheToolInAValidatedAllocationOnly() {
        String validated = allocation("org-1", AiUsageAllocation.Status.VALIDATED, true);
        String pending = allocation("org-1", AiUsageAllocation.Status.PENDING_VALIDATION, true);
        String foreign = allocation("org-2", AiUsageAllocation.Status.VALIDATED, true);
        aief("org-1", "user-1", DAY, "Claude Code CLI", true, validated);
        aief("org-1", "user-3", DAY, "Claude Code CLI", true, pending);
        aief("org-1", "user-4", DAY, "Claude Code CLI", true, foreign);

        assertThat(usages.existsAiefInValidatedAllocation("org-1", "user-1", DAY, CLAUDE)).isTrue();
        assertThat(usages.existsAiefInValidatedAllocation("org-1", "user-1", DAY, List.of("Codex CLI"))).isFalse();
        assertThat(usages.existsAiefInValidatedAllocation("org-1", "user-3", DAY, CLAUDE)).isFalse();
        assertThat(usages.existsAiefInValidatedAllocation("org-1", "user-4", DAY, CLAUDE)).isFalse();
    }

    @Test
    void should_findTheActiveAgentRowOfTheToolAndTheModelsOfTheDay() {
        AIUsage row = save("org-1", "user-1", DAY, true, true, null);
        save("org-1", "user-1", DAY, true, false, null);
        save("org-1", "user-2", DAY, true, true, null);
        mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(row.getId())),
                new org.springframework.data.mongodb.core.query.Update().set("tool", "claude-code").set("model", "claude-x"),
                AiUsageDocument.class);

        assertThat(usages.existsActiveAgentRow("org-1", "user-1", DAY, "claude-code")).isTrue();
        assertThat(usages.existsActiveAgentRow("org-1", "user-1", DAY, "codex")).isFalse();
        assertThat(usages.existsActiveAgentRow("org-1", "user-1", BEFORE, "claude-code")).isFalse();
        assertThat(usages.existsActiveAgentRow("org-2", "user-1", DAY, "claude-code")).isFalse();
        assertThat(usages.agentModelsOfDay("org-1", "user-1", DAY)).contains("claude-x");
        assertThat(usages.agentModelsOfDay("org-1", "user-9", DAY)).isEmpty();
    }

    @Test
    void should_findOnlyTheActiveUnallocatedAgentRowsOfTheDay() {
        AIUsage match = save("org-1", "user-1", DAY, true, true, null);
        save("org-1", "user-1", DAY, true, true, "alloc-1");
        save("org-1", "user-1", DAY, true, false, null);
        save("org-1", "user-1", DAY, false, true, null);
        save("org-1", "user-1", BEFORE, true, true, null);
        save("org-1", "user-2", DAY, true, true, null);
        save("org-2", "user-1", DAY, true, true, null);

        List<AIUsage> rows = usages.findUnallocatedAgentRows("org-1", "user-1", DAY);

        assertThat(rows).extracting(AIUsage::getId).containsExactly(match.getId());
    }

    @Test
    void should_findTheActiveAgentRowsSinceTheInstant() {
        AIUsage match = save("org-1", "user-1", DAY, true, true, "alloc-1");
        AIUsage old = save("org-1", "user-1", DAY, true, true, null);
        mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(old.getId())),
                new org.springframework.data.mongodb.core.query.Update().set("bucketStart", Instant.parse("2098-12-30T00:00:00Z")),
                AiUsageDocument.class);
        save("org-1", "user-1", DAY, true, false, null);
        save("org-1", "user-1", DAY, false, true, null);
        save("org-1", "user-2", DAY, true, true, null);
        save("org-2", "user-1", DAY, true, true, null);

        List<AIUsage> rows = usages.findActiveAgentRowsSince("org-1", "user-1", Instant.parse("2099-01-01T00:00:00Z"));

        assertThat(rows).extracting(AIUsage::getId).containsExactly(match.getId());
    }

    @Test
    void should_stampTheTaskOnlyOnRowsOfTheOrganization() {
        AIUsage mine = save("org-1", "user-1", DAY, true, true, null);
        AIUsage foreign = save("org-2", "user-1", DAY, true, true, null);
        AIUsage untouched = save("org-1", "user-1", DAY, true, true, null);

        usages.stampTask("org-1", List.of(mine.getId(), foreign.getId()), "wi-1", "proj-1", "AB#1", "ACTIVE_TASK");

        AIUsage a = reload(mine.getId());
        assertThat(a.getWorkItemId()).isEqualTo("wi-1");
        assertThat(a.getTaskRef()).isEqualTo("AB#1");
        assertThat(a.getAttributionSource()).isEqualTo("ACTIVE_TASK");
        assertThat(reload(foreign.getId()).getWorkItemId()).isNull();
        assertThat(reload(untouched.getId()).getWorkItemId()).isNull();
        usages.stampTask("org-1", List.of(), "wi-2", "p", "r", "x");
    }

    private void allocation(String user, LocalDate day, AiUsageAllocation.Status status,
                            AiUsageAllocation.AssignedBy by, boolean moved) {
        AiUsageAllocation a = new AiUsageAllocation(null, "org-1", user, day, List.of("u1"), List.of(), status, by,
                true, null, NOW, NOW);
        a.setMovedFromAgent(moved);
        allocations.save(a);
    }

    @Test
    void should_closeTheDayOnlyWithAValidatedNonAgentNonMovedAllocation() {
        allocation("user-1", DAY, AiUsageAllocation.Status.VALIDATED, AiUsageAllocation.AssignedBy.AGENT, false);
        allocation("user-1", DAY, AiUsageAllocation.Status.VALIDATED, AiUsageAllocation.AssignedBy.MANUAL, true);
        allocation("user-1", DAY, AiUsageAllocation.Status.PENDING_VALIDATION, AiUsageAllocation.AssignedBy.AUTO, false);
        assertThat(allocations.existsValidatedByOrganizationIdAndUserIdAndDay("org-1", "user-1", DAY)).isFalse();

        allocation("user-1", DAY, AiUsageAllocation.Status.VALIDATED, AiUsageAllocation.AssignedBy.MANUAL, false);

        assertThat(allocations.existsValidatedByOrganizationIdAndUserIdAndDay("org-1", "user-1", DAY)).isTrue();
        assertThat(allocations.existsValidatedByOrganizationIdAndUserIdAndDay("org-1", "user-2", DAY)).isFalse();
        assertThat(allocations.existsValidatedByOrganizationIdAndUserIdAndDay("org-2", "user-1", DAY)).isFalse();
        assertThat(allocations.existsValidatedByOrganizationIdAndUserIdAndDay("org-1", "user-1", BEFORE)).isFalse();
    }
}
