package andromeda.sei.ms.feature.vcs.infrastructure.persistence;

import andromeda.sei.ms.feature.vcs.domain.VcsProvider;
import andromeda.sei.ms.feature.vcs.domain.VcsRepoBinding;
import andromeda.sei.ms.feature.vcs.domain.VcsSyncState;
import andromeda.sei.ms.shared.testsupport.MongoIntegrationTest;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** branches-por-vinculo 2.2: the new binding fields survive a round trip, and an old document reads as legacy. */
@MongoIntegrationTest
class VcsRepoBindingBranchesMongoTest {

    private static final String ORG = "org-1";

    @Autowired private MongoTemplate mongoTemplate;

    private VcsRepoBindingRepositoryImpl bindings;

    @BeforeEach
    void setUp() {
        bindings = new VcsRepoBindingRepositoryImpl(mongoTemplate, new VcsDocumentMapper());
        mongoTemplate.dropCollection(VcsRepoBindingDocument.class);
    }

    @Test
    void should_roundTripBranchFields() {
        VcsRepoBinding binding = new VcsRepoBinding(null, ORG, "p1", "r1", VcsProvider.AZURE_REPOS, true);
        binding.setBranches(List.of("develop", "main"));
        binding.setProductionBranch("main");
        binding.setFilterPullRequestsByBranch(true);
        binding.setMissingBranches(List.of("old"));
        binding.setSyncState(VcsSyncState.RUNNING);
        Instant at = Instant.parse("2026-10-27T10:00:00Z");
        binding.setSyncStateAt(at);

        VcsRepoBinding saved = bindings.save(binding);
        VcsRepoBinding read = bindings.findByIdAndOrganizationIdAndActiveTrue(saved.getId(), ORG).orElseThrow();

        assertThat(read.getBranches()).containsExactly("develop", "main");
        assertThat(read.getProductionBranch()).isEqualTo("main");
        assertThat(read.isFilterPullRequestsByBranch()).isTrue();
        assertThat(read.getMissingBranches()).containsExactly("old");
        assertThat(read.getSyncState()).isEqualTo(VcsSyncState.RUNNING);
        assertThat(read.getSyncStateAt()).isEqualTo(at);
    }

    @Test
    void should_readOldDocumentWithoutTheFieldsAsLegacy() {
        mongoTemplate.getCollection(VcsRepoBindingDocument.COLLECTION).insertOne(new Document()
                .append("_id", "old-1").append("organizationId", ORG).append("projectId", "p1")
                .append("repositoryId", "r1").append("provider", "GITHUB").append("syncEnabled", true)
                .append("active", true).append("version", 0L));

        VcsRepoBinding read = bindings.findByIdAndOrganizationIdAndActiveTrue("old-1", ORG).orElseThrow();

        assertThat(read.getBranches()).isNull();
        assertThat(read.getProductionBranch()).isNull();
        assertThat(read.isFilterPullRequestsByBranch()).isFalse();
        assertThat(read.getMissingBranches()).isEmpty();
        assertThat(read.getSyncState()).isNull();
        assertThat(read.effectiveBranches("develop")).containsExactly("develop");
        assertThat(read.effectiveProductionBranch("develop")).isEqualTo("develop");
        assertThat(read.effectiveSyncState(Instant.now())).isEqualTo(VcsSyncState.PENDING);
    }
}
