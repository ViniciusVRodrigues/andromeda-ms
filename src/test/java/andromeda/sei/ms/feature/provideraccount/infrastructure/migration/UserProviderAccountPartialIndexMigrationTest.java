package andromeda.sei.ms.feature.provideraccount.infrastructure.migration;

import andromeda.sei.ms.shared.testsupport.MongoIntegrationTest;
import com.mongodb.client.model.IndexOptions;
import org.bson.Document;
import andromeda.sei.ms.shared.infrastructure.config.DeclaredIndexInitializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@MongoIntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class UserProviderAccountPartialIndexMigrationTest {

    private static final String COL = "user_provider_accounts";
    private static final String NEW_EXT = "uk_org_provider_external_active";
    private static final String NEW_USR = "uk_org_user_provider_active";

    @Autowired private MongoTemplate mongoTemplate;
    @Autowired private DeclaredIndexInitializer declaredIndexInitializer;

    private UserProviderAccountPartialIndexMigration migration;

    @BeforeEach
    void setUp() {
        migration = new UserProviderAccountPartialIndexMigration(mongoTemplate);
        mongoTemplate.dropCollection(COL);
    }

    /** O Mongo do teste e compartilhado: devolve a colecao com os indices declarados, como na subida. */
    @AfterEach
    void restoreDeclaredIndexes() {
        mongoTemplate.dropCollection(COL);
        declaredIndexInitializer.run(null);
    }

    private void createLegacyExternalIndex() {
        mongoTemplate.getCollection(COL).createIndex(
                new Document("organizationId", 1).append("provider", 1).append("externalUserId", 1),
                new IndexOptions().unique(true));
    }

    private void createLegacyUserIndex() {
        mongoTemplate.getCollection(COL).createIndex(
                new Document("organizationId", 1).append("userId", 1).append("provider", 1),
                new IndexOptions().unique(true));
    }

    private List<String> indexNames() {
        List<String> names = new ArrayList<>();
        mongoTemplate.getCollection(COL).listIndexes().forEach(d -> names.add(d.getString("name")));
        return names;
    }

    private Document index(String name) {
        for (Document d : mongoTemplate.getCollection(COL).listIndexes()) {
            if (name.equals(d.getString("name"))) return d;
        }
        return null;
    }

    private void insert(String id, String user, String ext, boolean active) {
        mongoTemplate.getCollection(COL).insertOne(new Document("_id", id).append("organizationId", "org-1")
                .append("provider", "GITHUB").append("userId", user).append("externalUserId", ext)
                .append("active", active));
    }

    @Test
    void shouldReplaceLegacyIndexesWithPartialOnes(CapturedOutput output) {
        createLegacyExternalIndex();
        createLegacyUserIndex();
        insert("a", "u1", "e1", true);
        assertThat(indexNames()).hasSize(3);

        migration.migrate();

        assertThat(indexNames()).containsExactlyInAnyOrder("_id_", NEW_EXT, NEW_USR);
        assertThat(index(NEW_EXT).getBoolean("unique")).isTrue();
        assertThat(index(NEW_EXT).get("partialFilterExpression", Document.class)).containsEntry("active", true);
        assertThat(index(NEW_USR).get("partialFilterExpression", Document.class)).containsEntry("active", true);
        assertThat(output.getAll()).contains("UserProviderAccountPartialIndexMigration: created");
    }

    @Test
    void shouldBeIdempotent() {
        createLegacyExternalIndex();
        createLegacyUserIndex();
        migration.migrate();
        Document before = index(NEW_EXT);

        migration.migrate();

        assertThat(indexNames()).containsExactlyInAnyOrder("_id_", NEW_EXT, NEW_USR);
        assertThat(index(NEW_EXT)).isEqualTo(before);
    }

    @Test
    void shouldLeaveIndexesUntouchedAndLogErrorWhenActiveDuplicatesExist(CapturedOutput output) {
        createLegacyUserIndex();
        insert("dup-1", "u1", "e1", true);
        insert("dup-2", "u2", "e1", true);

        migration.migrate();

        assertThat(indexNames()).hasSize(2).doesNotContain(NEW_EXT, NEW_USR);
        assertThat(output.getAll()).contains("duplicate active accounts").contains("dup-1").contains("dup-2");
    }

    @Test
    void shouldIgnoreInactiveDuplicates() {
        insert("i1", "u1", "e1", false);
        insert("i2", "u2", "e1", false);

        migration.migrate();

        assertThat(indexNames()).containsExactlyInAnyOrder("_id_", NEW_EXT, NEW_USR);
    }

    @Test
    void shouldNotFailWhenCollectionDoesNotExist() {
        migration.migrate();

        assertThat(mongoTemplate.collectionExists(COL)).isFalse();
    }
}
