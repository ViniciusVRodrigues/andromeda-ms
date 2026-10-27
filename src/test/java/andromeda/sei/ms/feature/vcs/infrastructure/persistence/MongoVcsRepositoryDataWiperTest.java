package andromeda.sei.ms.feature.vcs.infrastructure.persistence;

import andromeda.sei.ms.shared.testsupport.MongoIntegrationTest;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** branches-por-vinculo 2.8: the per-repository wipe touches one (organization, repository) pair only. */
@MongoIntegrationTest
class MongoVcsRepositoryDataWiperTest {

    private static final String[] DERIVED = {VcsReviewEventDocument.COLLECTION, VcsPullRequestDocument.COLLECTION,
            VcsCommitDocument.COLLECTION};

    @Autowired private MongoTemplate mongoTemplate;
    private MongoVcsRepositoryDataWiper wiper;

    @BeforeEach
    void setUp() {
        wiper = new MongoVcsRepositoryDataWiper(mongoTemplate);
        for (String collection : DERIVED) mongoTemplate.dropCollection(collection);
        mongoTemplate.dropCollection(VcsRepositoryDocument.COLLECTION);
        mongoTemplate.dropCollection(VcsRepoBindingDocument.COLLECTION);
        for (String org : new String[]{"org-1", "org-2"}) {
            for (String repo : new String[]{"repo-1", "repo-2"}) {
                for (String collection : DERIVED) {
                    mongoTemplate.getCollection(collection).insertOne(new Document("organizationId", org)
                            .append("repositoryId", repo).append("n", 1));
                }
            }
        }
        mongoTemplate.getCollection(VcsRepositoryDocument.COLLECTION).insertOne(
                new Document("_id", "repo-1").append("organizationId", "org-1"));
        mongoTemplate.getCollection(VcsRepoBindingDocument.COLLECTION).insertOne(
                new Document("_id", "b1").append("organizationId", "org-1").append("repositoryId", "repo-1"));
    }

    @Test
    void should_deleteOnlyThePairOrgRepo_andKeepTheRepositoryAndBinding() {
        Map<String, Long> removed = wiper.wipeRepository("org-1", "repo-1");

        assertThat(removed.values()).containsOnly(1L);
        for (String collection : DERIVED) {
            assertThat(count(collection, "org-1", "repo-1")).as(collection + " org-1/repo-1").isZero();
            assertThat(count(collection, "org-1", "repo-2")).as(collection + " org-1/repo-2").isEqualTo(1);
            assertThat(count(collection, "org-2", "repo-1")).as(collection + " org-2/repo-1").isEqualTo(1);
            assertThat(count(collection, "org-2", "repo-2")).as(collection + " org-2/repo-2").isEqualTo(1);
        }
        assertThat(mongoTemplate.getCollection(VcsRepositoryDocument.COLLECTION).countDocuments()).isEqualTo(1);
        assertThat(mongoTemplate.getCollection(VcsRepoBindingDocument.COLLECTION).countDocuments()).isEqualTo(1);
    }

    @Test
    void should_refuseAMissingScope() {
        assertThatThrownBy(() -> wiper.wipeRepository(null, "repo-1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> wiper.wipeRepository("org-1", null)).isInstanceOf(IllegalArgumentException.class);
    }

    private long count(String collection, String org, String repo) {
        return mongoTemplate.getCollection(collection)
                .countDocuments(new Document("organizationId", org).append("repositoryId", repo));
    }
}
