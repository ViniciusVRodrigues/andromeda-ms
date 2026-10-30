package andromeda.sei.ms.feature.aiusage.infrastructure.persistence;

import andromeda.sei.ms.feature.aiusage.domain.AIUsage;
import andromeda.sei.ms.shared.testsupport.MongoIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** extensao-fora-dos-totais D5: a re-upload does not revive a row the agent turned off. */
@MongoIntegrationTest
class AiUsageUpsertSupersededMongoTest {

    private static final LocalDate DAY = LocalDate.of(2099, 1, 1);

    @Autowired private MongoTemplate mongoTemplate;
    @Autowired private AiUsageMongoRepository aiUsageMongo;
    private AiUsageRepositoryImpl repo;

    @BeforeEach
    void setUp() {
        repo = new AiUsageRepositoryImpl(aiUsageMongo, new AiUsageDocumentMapper(), mongoTemplate);
        mongoTemplate.remove(new Query(), "ai_usages");
    }

    private static AIUsage usage(long output) {
        return new AIUsage(null, "o1", "u1", null, "ds-1", DAY, "claude", "ws", null, "m", null, 10, output, 1,
                "Claude Code", null, null, null, true, null, null, null);
    }

    private List<AiUsageDocument> all() {
        return mongoTemplate.find(Query.query(Criteria.where("organizationId").is("o1")), AiUsageDocument.class);
    }

    @Test
    void should_insertActive_when_newRow() {
        repo.upsert(usage(5));
        assertThat(all()).singleElement().satisfies(d -> {
            assertThat(d.isActive()).isTrue();
            assertThat(d.getOutputTokens()).isEqualTo(5);
        });
    }

    @Test
    void should_updateTokensAndStayInactive_when_rowWasSupersededByTheAgent() {
        repo.upsert(usage(5));
        AiUsageDocument d = all().get(0);
        d.setActive(false);
        d.setSupersededBy("AGENT");
        mongoTemplate.save(d);

        repo.upsert(usage(99));

        assertThat(all()).singleElement().satisfies(r -> {
            assertThat(r.getOutputTokens()).isEqualTo(99);
            assertThat(r.isActive()).isFalse();
            assertThat(r.getSupersededBy()).isEqualTo("AGENT");
        });
    }

    @Test
    void should_reactivate_when_rowIsInactiveWithoutSupersededBy() {
        repo.upsert(usage(5));
        AiUsageDocument d = all().get(0);
        d.setActive(false);
        mongoTemplate.save(d);

        repo.upsert(usage(7));

        assertThat(all()).singleElement().satisfies(r -> {
            assertThat(r.isActive()).isTrue();
            assertThat(r.getOutputTokens()).isEqualTo(7);
        });
    }
}
