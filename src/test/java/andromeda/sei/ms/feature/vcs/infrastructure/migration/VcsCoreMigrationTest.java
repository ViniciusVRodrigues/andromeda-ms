package andromeda.sei.ms.feature.vcs.infrastructure.migration;

import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * vcs-core tasks 8.1–8.4: {@code V__vcs_core} against a mocked {@link MongoTemplate} (no embedded
 * Mongo under Boot 4 here; the real run twice is the migration dry-run on a copy of the local
 * database). Idempotency is structural: every copy is an upsert on the source {@code _id} carrying
 * only {@code $setOnInsert}, and a pending repository is created once and reused afterwards.
 */
@ExtendWith(MockitoExtension.class)
class VcsCoreMigrationTest {

    private static final String ORG = "org-1";
    private static final ObjectId REPO_ID = new ObjectId();
    private static final ObjectId PR_ID = new ObjectId();
    private static final Date T0 = new Date(1_756_684_800_000L);

    @Mock private MongoTemplate mongoTemplate;

    private final Map<String, List<Document>> sources = new HashMap<>();
    private final Map<String, List<Upsert>> upserts = new HashMap<>();
    private final List<Document> vcsRepositories = new ArrayList<>();

    private record Upsert(Document key, Document update) {}

    private VcsCoreMigration migration;

    @BeforeEach
    void setUp() {
        migration = new VcsCoreMigration(mongoTemplate);
        sources.put("github_repos", List.of(new Document("_id", REPO_ID).append("organizationId", ORG)
                .append("externalId", "1001").append("fullName", "acme/api").append("defaultBranch", "main")
                .append("active", true).append("createdAt", T0).append("updatedAt", T0).append("version", 3L)));
        sources.put("github_repo_bindings", List.of(
                binding(new ObjectId(), "acme/api", T0),
                binding(new ObjectId(), "acme/new", T0)));
        sources.put("github_pull_requests", List.of(new Document("_id", PR_ID).append("organizationId", ORG)
                .append("repoId", REPO_ID.toHexString()).append("externalId", "9").append("number", 9)
                .append("status", "MERGED").append("authorGithubLogin", "alice").append("authorUserId", "u-1")
                .append("cycleTimeHours", 10.0).append("timeToFirstReviewHours", 2.0).append("createdAt", T0)
                .append("active", true)));
        sources.put("github_commits", List.of(new Document("_id", new ObjectId()).append("organizationId", ORG)
                .append("repoId", REPO_ID.toHexString()).append("sha", "abc").append("authorGithubLogin", "alice")
                .append("authoredAt", T0).append("active", true)));
        sources.put("github_reviews", List.of(
                review("DISMISSED", "dave", null, T0),
                review("APPROVED", "renovate[bot]", null, T0),
                review("PENDING", "erin", null, null),
                review("CHANGES_REQUESTED", "bob", true, T0)));
        sources.put("github_configs", List.of(new Document("_id", new ObjectId()).append("organizationId", new ObjectId().toHexString())
                .append("workItemLinkConfig", new Document("enabled", true).append("prefixes", List.of("AB#", "WI-")))));

        lenient().when(mongoTemplate.collectionExists(anyString())).thenAnswer(inv -> sources.containsKey(inv.<String>getArgument(0)));
        lenient().when(mongoTemplate.stream(any(Query.class), eq(Document.class), anyString()))
                .thenAnswer(inv -> sources.getOrDefault(inv.<String>getArgument(2), List.of()).stream());
        lenient().when(mongoTemplate.find(any(Query.class), eq(Document.class), eq("github_repos"))).thenAnswer(inv -> {
            String fullName = inv.<Query>getArgument(0).getQueryObject().getString("fullName");
            return sources.get("github_repos").stream().filter(d -> fullName.equals(d.getString("fullName"))).toList();
        });
        lenient().when(mongoTemplate.find(any(Query.class), eq(Document.class), eq("github_configs")))
                .thenAnswer(inv -> sources.get("github_configs"));
        lenient().when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("vcs_repositories"))).thenAnswer(inv -> {
            String displayName = inv.<Query>getArgument(0).getQueryObject().getString("displayName");
            return vcsRepositories.stream().filter(d -> displayName.equals(d.getString("displayName"))).findFirst().orElse(null);
        });
        lenient().when(mongoTemplate.insert(any(Document.class), eq("vcs_repositories"))).thenAnswer(inv -> {
            vcsRepositories.add(inv.getArgument(0));
            return inv.getArgument(0);
        });
        lenient().when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq("organizations")))
                .thenReturn(UpdateResult.acknowledged(1, 1L, null));
        lenient().when(mongoTemplate.bulkOps(eq(BulkOperations.BulkMode.UNORDERED), anyString())).thenAnswer(inv -> {
            String target = inv.getArgument(1);
            BulkOperations bulk = org.mockito.Mockito.mock(BulkOperations.class);
            when(bulk.upsert(any(Query.class), any(Update.class))).thenAnswer(call -> {
                upserts.computeIfAbsent(target, t -> new ArrayList<>()).add(new Upsert(
                        call.<Query>getArgument(0).getQueryObject(), call.<Update>getArgument(1).getUpdateObject()));
                return bulk;
            });
            return bulk;
        });
    }

    @Test
    void should_copyRepositories_keepingIdAndMappingIdentity() {
        migration.run(null);

        Document repo = onlyInsert("vcs_repositories");
        assertThat(upserts.get("vcs_repositories").getFirst().key()).containsEntry("_id", REPO_ID);
        assertThat(repo).containsEntry("provider", "GITHUB").containsEntry("externalRepoId", "1001")
                .containsEntry("displayName", "acme/api").containsEntry("defaultBranch", "main")
                .containsEntry("organizationId", ORG);
    }

    @Test
    void should_convertBindingKey_toInternalRepositoryId_andPreserveWatermark() {
        migration.run(null);

        List<Document> bindings = inserts("vcs_repo_bindings");
        Document synced = bindings.stream().filter(b -> REPO_ID.toHexString().equals(b.get("repositoryId")))
                .findFirst().orElseThrow();
        assertThat(synced).containsEntry("lastSyncAt", T0).containsEntry("provider", "GITHUB")
                .containsEntry("projectId", "proj-1").containsEntry("syncEnabled", true);
    }

    @Test
    void should_createPendingRepository_forBindingNeverSynced_withoutWatermark() {
        migration.run(null);

        assertThat(vcsRepositories).singleElement().satisfies(pending -> {
            assertThat(pending.getString("displayName")).isEqualTo("acme/new");
            assertThat(pending).doesNotContainKey("externalRepoId");
        });
        String pendingId = ((ObjectId) vcsRepositories.getFirst().get("_id")).toHexString();
        Document binding = inserts("vcs_repo_bindings").stream()
                .filter(b -> pendingId.equals(b.get("repositoryId"))).findFirst().orElseThrow();
        assertThat(binding).doesNotContainKey("lastSyncAt");
    }

    @Test
    void should_renameAuthorLogin_onPullRequestsAndCommits() {
        migration.run(null);

        assertThat(onlyInsert("vcs_pull_requests")).containsEntry("authorLogin", "alice")
                .containsEntry("repositoryId", REPO_ID.toHexString()).containsEntry("cycleTimeHours", 10.0)
                .containsEntry("timeToFirstReviewHours", 2.0).doesNotContainKey("authorGithubLogin");
        assertThat(onlyInsert("vcs_commits")).containsEntry("authorLogin", "alice").containsEntry("sha", "abc");
    }

    @Test
    void should_convertReviewsToEvents_dismissedAsCommented_pendingDropped_botsFlagged() {
        migration.run(null);

        List<Document> events = inserts("vcs_review_events");
        assertThat(events).extracting(e -> e.getString("kind"))
                .containsExactly("COMMENTED", "APPROVED", "CHANGES_REQUESTED");
        assertThat(events).extracting(e -> e.getBoolean("isBot")).containsExactly(false, true, true);
        assertThat(events).allSatisfy(e -> {
            assertThat(e).containsEntry("repositoryId", REPO_ID.toHexString());
            assertThat(e).containsEntry("pullRequestId", PR_ID.toHexString());
            assertThat(e).containsEntry("deleted", false);
        });
    }

    @Test
    void should_copyWorkItemLinkConfig_onlyToOrganizationsWithoutOne() {
        migration.run(null);

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).updateFirst(query.capture(), update.capture(), eq("organizations"));
        assertThat(query.getValue().getQueryObject().get("vcsSettings.workItemLink", Document.class))
                .containsEntry("$exists", false);
        Document link = update.getValue().getUpdateObject().get("$set", Document.class)
                .get("vcsSettings.workItemLink", Document.class);
        assertThat(link).containsEntry("enabled", true).containsEntry("prefixes", List.of("AB#", "WI-"));
    }

    @Test
    void should_onlyEverInsert_neverOverwrite_soASecondRunChangesNothing() {
        migration.run(null);
        Map<String, Integer> firstRun = new HashMap<>();
        upserts.forEach((target, ops) -> firstRun.put(target, ops.size()));

        migration.run(null);

        upserts.forEach((target, ops) -> {
            assertThat(ops).hasSize(firstRun.get(target) * 2);
            assertThat(ops).allSatisfy(op -> {
                assertThat(op.update().keySet()).containsExactly("$setOnInsert");
                assertThat(op.key().keySet()).containsExactly("_id");
            });
            List<Object> keys = ops.stream().map(op -> op.key().get("_id")).toList();
            assertThat(keys.subList(firstRun.get(target), keys.size()))
                    .containsExactlyElementsOf(keys.subList(0, firstRun.get(target)));
        });
        assertThat(vcsRepositories).hasSize(1);
        verify(mongoTemplate, times(1)).insert(any(Document.class), eq("vcs_repositories"));
    }

    @Test
    void should_skipMissingSourceCollections() {
        sources.clear();

        migration.run(null);

        verify(mongoTemplate, never()).bulkOps(any(BulkOperations.BulkMode.class), anyString());
    }

    private Document onlyInsert(String target) {
        assertThat(upserts.get(target)).hasSize(1);
        return upserts.get(target).getFirst().update().get("$setOnInsert", Document.class);
    }

    private List<Document> inserts(String target) {
        return upserts.get(target).stream().map(op -> op.update().get("$setOnInsert", Document.class)).toList();
    }

    private static Document binding(ObjectId id, String fullName, Date lastSyncAt) {
        return new Document("_id", id).append("organizationId", ORG).append("projectId", "proj-1")
                .append("repoId", fullName).append("syncEnabled", true).append("lastSyncAt", lastSyncAt)
                .append("active", true);
    }

    private static Document review(String result, String login, Boolean bot, Date submittedAt) {
        Document review = new Document("_id", new ObjectId()).append("organizationId", ORG)
                .append("pullRequestId", PR_ID.toHexString()).append("externalId", "r-" + login)
                .append("reviewerGithubLogin", login).append("result", result).append("active", true);
        if (bot != null) review.append("reviewerIsBot", bot);
        if (submittedAt != null) review.append("submittedAt", submittedAt);
        return review;
    }
}
