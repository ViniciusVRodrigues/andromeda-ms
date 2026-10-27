package andromeda.sei.ms.feature.workitem.infrastructure.migration;

import andromeda.sei.ms.shared.testsupport.MongoIntegrationTest;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@MongoIntegrationTest
class LegacySpFieldsUnsetMigrationTest {

    private static final String COLLECTION = "work_items";

    @Autowired
    private MongoTemplate mongo;

    private LegacySpFieldsUnsetMigration migration;

    @BeforeEach
    void setUp() {
        mongo.dropCollection(COLLECTION);
        migration = new LegacySpFieldsUnsetMigration(mongo);
    }

    private Document raw(String id) {
        return mongo.findById(id, Document.class, COLLECTION);
    }

    @Test
    void should_unsetBothFields_andKeepTheRest_acrossOrganizations() {
        mongo.insert(new Document("_id", "a").append("organizationId", "org-1").append("storyPoints", 5.0)
                .append("tokensPerSp", 10.0).append("complexity", 3.0), COLLECTION);
        mongo.insert(new Document("_id", "b").append("organizationId", "org-2").append("tokensPerSp", 7.0), COLLECTION);
        mongo.insert(new Document("_id", "c").append("organizationId", "org-2").append("storyPoints", 2.0), COLLECTION);

        migration.run(null);

        assertThat(raw("a")).doesNotContainKeys("storyPoints", "tokensPerSp").containsEntry("complexity", 3.0);
        assertThat(raw("b")).doesNotContainKey("tokensPerSp");
        assertThat(raw("c")).doesNotContainKey("storyPoints");
    }

    @Test
    void should_beIdempotent_andLeaveDocumentsWithoutTheFieldsUntouched() {
        mongo.insert(new Document("_id", "a").append("organizationId", "org-1").append("storyPoints", 5.0), COLLECTION);
        mongo.insert(new Document("_id", "clean").append("organizationId", "org-1").append("effort", 4.0), COLLECTION);
        Document before = raw("clean");

        migration.run(null);
        migration.run(null);

        assertThat(raw("a")).doesNotContainKey("storyPoints");
        assertThat(raw("clean")).isEqualTo(before);
    }

    @Test
    void should_reportTheModifiedCount_onlyOnTheFirstRun() {
        mongo.insert(new Document("_id", "a").append("storyPoints", 5.0), COLLECTION);
        mongo.insert(new Document("_id", "b").append("tokensPerSp", 1.0), COLLECTION);
        mongo.insert(new Document("_id", "c").append("effort", 1.0), COLLECTION);

        assertThat(migration.unsetLegacyFields()).isEqualTo(2);
        assertThat(migration.unsetLegacyFields()).isZero();
    }
}
