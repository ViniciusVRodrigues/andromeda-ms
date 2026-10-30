package andromeda.sei.ms.feature.aiusage.infrastructure.persistence;

import andromeda.sei.ms.feature.aiusage.domain.AiefOverlapRepository.AgentCoverage;
import andromeda.sei.ms.feature.aiusage.domain.AiefOverlapRepository.Candidate;
import andromeda.sei.ms.shared.testsupport.MongoIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@MongoIntegrationTest
class AiefOverlapRepositoryMongoTest {

    private static final LocalDate DAY = LocalDate.of(2099, 1, 1);
    private static final List<String> EDITORS = List.of("Claude Code", "Codex CLI");

    @Autowired private MongoTemplate mongoTemplate;
    private AiefOverlapRepositoryImpl repo;

    @BeforeEach
    void setUp() {
        repo = new AiefOverlapRepositoryImpl(mongoTemplate);
        mongoTemplate.remove(new Query(), "ai_usages");
    }

    private void save(String id, String org, String user, LocalDate day, String editor, boolean active,
                      String source, String tool, String supersededBy) {
        mongoTemplate.save(AiUsageDocument.builder().id(id).organizationId(org).userId(user).day(day)
                .editor(editor).active(active).source(source).tool(tool).supersededBy(supersededBy)
                .model("m").datasetId("d-" + id).build());
    }

    private AiUsageDocument get(String id) {
        return mongoTemplate.findById(id, AiUsageDocument.class);
    }

    @Test
    void should_findOnlyExtensionCandidates_ofTheOrganization() {
        save("a", "o1", "u1", DAY, "Claude Code", true, null, null, null);
        save("b", "o1", "u1", DAY, "Claude Code", false, null, null, "AGENT");
        save("c", "o1", "u1", DAY, "Claude Code", false, null, null, null);
        save("d", "o1", "u1", DAY, "VS Code", true, null, null, null);
        save("e", "o1", "u1", DAY, "claude-code", true, "AGENT", "claude-code", null);
        save("f", "o2", "u1", DAY, "Claude Code", true, null, null, null);
        save("g", "o1", "u1", DAY.minusDays(30), "Claude Code", true, null, null, null);

        assertThat(repo.findCandidates("o1", EDITORS, null)).extracting(Candidate::id)
                .containsExactlyInAnyOrder("a", "b", "g");
        assertThat(repo.findCandidates("o1", EDITORS, DAY.minusDays(7))).extracting(Candidate::id)
                .containsExactlyInAnyOrder("a", "b");
    }

    @Test
    void should_findCoverage_onlyOfActiveAgentRows_ofTheOrganization() {
        save("a1", "o1", "u1", DAY, "claude-code", true, "AGENT", "claude-code", null);
        save("a2", "o1", "u1", DAY, "claude-code", true, "AGENT", "claude-code", null);
        save("a3", "o1", "u1", DAY, "codex", false, "AGENT", "codex", null);
        save("a4", "o2", "u1", DAY, "codex", true, "AGENT", "codex", null);
        Candidate cand = new Candidate("x", "u1", DAY, "Claude Code", true, null);

        Set<AgentCoverage> cov = repo.findAgentCoverage("o1", List.of(cand), List.of("claude-code", "codex"));

        assertThat(cov).containsExactly(new AgentCoverage("u1", DAY, "claude-code"));
    }

    @Test
    void should_deactivateAndReactivate_onlyWhenStateStillMatches_andOrg() {
        save("a", "o1", "u1", DAY, "Claude Code", true, null, null, null);
        save("b", "o1", "u1", DAY, "Claude Code", false, null, null, "AGENT");
        save("c", "o1", "u1", DAY, "Claude Code", false, null, null, null);
        save("d", "o2", "u1", DAY, "Claude Code", true, null, null, null);

        assertThat(repo.deactivate("o1", List.of("a", "b", "d"))).isEqualTo(1);
        assertThat(get("a").isActive()).isFalse();
        assertThat(get("a").getSupersededBy()).isEqualTo("AGENT");
        assertThat(get("d").isActive()).isTrue();

        assertThat(repo.reactivate("o1", List.of("a", "b", "c"))).isEqualTo(2);
        assertThat(get("a").isActive()).isTrue();
        assertThat(get("a").getSupersededBy()).isNull();
        assertThat(get("b").isActive()).isTrue();
        assertThat(get("c").isActive()).isFalse();
    }
}
