package andromeda.sei.ms.feature.role.infrastructure.migration;

import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * vcs-core task 7.2 (rollback-safe revision) — the migration is additive: every role (default and
 * custom, any organization) ends up holding both codes of each renamed pair, so rolling the deploy
 * back to code that checks {@code GITHUB_*} keeps everyone's access. Nothing is ever {@code $pull}ed.
 * Against a mocked {@link MongoTemplate}: the assertions are on the filters and update operators,
 * which is where idempotency lives.
 */
@ExtendWith(MockitoExtension.class)
class ReposPermissionsMigrationTest {

    private static final Map<String, String> PAIRS = Map.of(
            "GITHUB_READ", "REPOS_READ",
            "GITHUB_CONFIGURE", "REPOS_CONFIGURE",
            "REPOS_READ", "GITHUB_READ",
            "REPOS_CONFIGURE", "GITHUB_CONFIGURE");

    @Mock private MongoTemplate mongoTemplate;

    private ReposPermissionsMigration migration;

    private record RoleWrite(Document filter, Document operations) {
        Document codes() { return filter.get("permissionCodes", Document.class); }
        Object present() { return codes().get("$all", List.class).getFirst(); }
        Object missing() { return codes().get("$nin", List.class).getFirst(); }
        Object added() { return operations.get("$addToSet", Document.class).get("permissionCodes"); }
    }

    @BeforeEach
    void setUp() {
        migration = new ReposPermissionsMigration(mongoTemplate);
        when(mongoTemplate.updateMulti(any(Query.class), any(Update.class), any(String.class)))
                .thenReturn(UpdateResult.acknowledged(1, 1L, null));
    }

    @Test
    void should_addEachMissingSideOfThePair_andNeverPullTheLegacyCode() {
        migration.run(null);

        List<RoleWrite> writes = captureRoleWrites();
        assertThat(writes).allSatisfy(write -> {
            assertThat(write.filter()).doesNotContainKey("organizationId"); // every org, default and custom
            assertThat(PAIRS.get((String) write.present())).isEqualTo(write.missing());
            assertThat(write.added()).isEqualTo(write.missing());
            assertThat(write.operations()).doesNotContainKey("$pull");
        });
        assertThat(writes).extracting(RoleWrite::added)
                .containsExactlyInAnyOrder("REPOS_READ", "REPOS_CONFIGURE", "GITHUB_READ", "GITHUB_CONFIGURE");
    }

    /** Already-migrated DBs (the earlier version pulled GITHUB_*) get the legacy mirror back. */
    @Test
    void should_reAddLegacyMirror_whereOnlyTheCurrentCodeIsPresent() {
        migration.run(null);

        assertThat(captureRoleWrites()).anySatisfy(write -> {
            assertThat(write.present()).isEqualTo("REPOS_READ");
            assertThat(write.missing()).isEqualTo("GITHUB_READ");
            assertThat(write.added()).isEqualTo("GITHUB_READ");
        });
    }

    /** Idempotent: each filter excludes roles already holding the added code, so a re-run matches nothing. */
    @Test
    void should_onlyMatchRolesMissingTheAddedCode_soReRunsModifyNothing() {
        migration.run(null);

        assertThat(captureRoleWrites()).allSatisfy(write ->
                assertThat(write.codes().get("$nin", List.class)).containsExactly(write.added()));
    }

    @Test
    void should_deactivateLegacyCatalogEntries() {
        migration.run(null);

        ArgumentCaptor<Query> queries = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> updates = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate, times(2)).updateMulti(queries.capture(), updates.capture(), eq("permissions"));
        assertThat(queries.getAllValues()).extracting(q -> q.getQueryObject().getString("code"))
                .containsExactlyInAnyOrder("GITHUB_READ", "GITHUB_CONFIGURE");
        assertThat(updates.getAllValues()).allSatisfy(u ->
                assertThat(u.getUpdateObject().get("$set", Document.class)).containsEntry("active", false));
    }

    private List<RoleWrite> captureRoleWrites() {
        ArgumentCaptor<Query> queries = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> updates = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate, times(4)).updateMulti(queries.capture(), updates.capture(), eq("roles"));
        return IntStream.range(0, queries.getAllValues().size())
                .mapToObj(i -> new RoleWrite(queries.getAllValues().get(i).getQueryObject(),
                        updates.getAllValues().get(i).getUpdateObject()))
                .toList();
    }
}
