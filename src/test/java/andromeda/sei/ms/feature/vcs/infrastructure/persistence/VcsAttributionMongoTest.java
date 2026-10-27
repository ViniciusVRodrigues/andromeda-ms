package andromeda.sei.ms.feature.vcs.infrastructure.persistence;

import andromeda.sei.ms.feature.vcs.domain.VcsAttribution;
import andromeda.sei.ms.feature.vcs.domain.VcsProvider;
import andromeda.sei.ms.shared.testsupport.MongoIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Real Mongo (Testcontainers): the bulk, ownerless-only, tenant-scoped authorship attribution. */
@MongoIntegrationTest
class VcsAttributionMongoTest {

    private static final String ORG = "org-1";
    private static final String OTHER_ORG = "org-2";
    private static final String USER = "user-1";

    @Autowired private MongoTemplate mongoTemplate;

    private VcsCommitRepositoryImpl commits;
    private VcsPullRequestRepositoryImpl pullRequests;
    private VcsReviewEventRepositoryImpl events;

    @BeforeEach
    void setUp() {
        VcsDocumentMapper mapper = new VcsDocumentMapper();
        commits = new VcsCommitRepositoryImpl(mongoTemplate, mapper);
        pullRequests = new VcsPullRequestRepositoryImpl(mongoTemplate, mapper);
        events = new VcsReviewEventRepositoryImpl(mongoTemplate, mapper);
        mongoTemplate.dropCollection(VcsCommitDocument.class);
        mongoTemplate.dropCollection(VcsPullRequestDocument.class);
        mongoTemplate.dropCollection(VcsReviewEventDocument.class);
    }

    private void commit(String org, String sha, VcsProvider provider, String externalId, String login, String email,
                        String owner) {
        mongoTemplate.insert(VcsCommitDocument.builder().organizationId(org).repositoryId("repo-1")
                .provider(provider).sha(sha).authorExternalId(externalId).authorLogin(login).authorEmail(email)
                .authorUserId(owner).authoredAt(Instant.now()).createdAt(Instant.now()).build());
    }

    private String ownerOfCommit(String org, String sha) {
        return mongoTemplate.findOne(Query.query(Criteria.where("organizationId").is(org).and("sha").is(sha)),
                VcsCommitDocument.class).getAuthorUserId();
    }

    private void pullRequest(String org, String repo, String externalId, String authorExternalId, String owner) {
        mongoTemplate.insert(VcsPullRequestDocument.builder().organizationId(org).repositoryId(repo)
                .provider(VcsProvider.GITHUB).externalId(externalId).authorExternalId(authorExternalId)
                .authorUserId(owner).createdAt(Instant.now()).build());
    }

    private String ownerOfPullRequest(String org, String externalId) {
        return mongoTemplate.findOne(Query.query(Criteria.where("organizationId").is(org)
                .and("externalId").is(externalId)), VcsPullRequestDocument.class).getAuthorUserId();
    }

    private void event(String org, String repo, String eventId, String actorExternalId, String owner) {
        mongoTemplate.insert(VcsReviewEventDocument.builder().organizationId(org).pullRequestId("pr-1")
                .repositoryId(repo).provider(VcsProvider.GITHUB).externalEventId(eventId)
                .actorExternalId(actorExternalId).actorUserId(owner).occurredAt(Instant.now())
                .createdAt(Instant.now()).build());
    }

    private String ownerOfEvent(String org, String eventId) {
        return mongoTemplate.findOne(Query.query(Criteria.where("organizationId").is(org)
                .and("externalEventId").is(eventId)), VcsReviewEventDocument.class).getActorUserId();
    }

    @Test
    void commits_byExternalId_shouldAttributeOnlyOwnerlessOfTheOrganizationAndProvider() {
        commit(ORG, "a", VcsProvider.GITHUB, "58421", "octo", null, null);
        commit(ORG, "b", VcsProvider.GITHUB, "58421", "octo", null, "someone-else");
        commit(OTHER_ORG, "c", VcsProvider.GITHUB, "58421", "octo", null, null);
        commit(ORG, "d", VcsProvider.AZURE_REPOS, "58421", "octo", null, null);
        commit(ORG, "e", VcsProvider.GITHUB, "999", "octo", null, null);

        long modified = commits.attributeByAuthorExternalIds(ORG, VcsProvider.GITHUB, List.of("58421"), USER);

        assertThat(modified).isEqualTo(1);
        assertThat(ownerOfCommit(ORG, "a")).isEqualTo(USER);
        assertThat(ownerOfCommit(ORG, "b")).isEqualTo("someone-else");
        assertThat(ownerOfCommit(OTHER_ORG, "c")).isNull();
        assertThat(ownerOfCommit(ORG, "d")).isNull();
        assertThat(ownerOfCommit(ORG, "e")).isNull();
    }

    @Test
    void commits_byLegacyLogin_shouldIgnoreCase_onlyWithoutExternalId_andTakeRegexCharsLiterally() {
        commit(ORG, "old", VcsProvider.GITHUB, null, "OctoCat", null, null);
        commit(ORG, "withId", VcsProvider.GITHUB, "999", "octocat", null, null);
        commit(ORG, "dot", VcsProvider.GITHUB, null, "axb", null, null);
        commit(ORG, "dotReal", VcsProvider.GITHUB, null, "a.b", null, null);
        commit(ORG, "prefix", VcsProvider.GITHUB, null, "octocat-two", null, null);
        commit(OTHER_ORG, "otherOrg", VcsProvider.GITHUB, null, "octocat", null, null);

        long first = commits.attributeByLegacyAuthorLogin(ORG, VcsProvider.GITHUB, "octocat", USER);
        long second = commits.attributeByLegacyAuthorLogin(ORG, VcsProvider.GITHUB, "A.B", USER);

        assertThat(first).isEqualTo(1);
        assertThat(second).isEqualTo(1);
        assertThat(ownerOfCommit(ORG, "old")).isEqualTo(USER);
        assertThat(ownerOfCommit(ORG, "withId")).isNull();
        assertThat(ownerOfCommit(ORG, "prefix")).isNull();
        assertThat(ownerOfCommit(ORG, "dotReal")).isEqualTo(USER);
        assertThat(ownerOfCommit(ORG, "dot")).isNull();
        assertThat(ownerOfCommit(OTHER_ORG, "otherOrg")).isNull();
    }

    @Test
    void commits_byEmail_shouldIgnoreCase_andKeepExistingOwner() {
        commit(ORG, "a", VcsProvider.AZURE_REPOS, null, "Ana", "Ana@Az.com", null);
        commit(ORG, "b", VcsProvider.AZURE_REPOS, null, "Ana", "ana@az.com", "someone-else");
        commit(ORG, "c", VcsProvider.AZURE_REPOS, null, "Bob", "bob@az.com", null);
        commit(OTHER_ORG, "d", VcsProvider.AZURE_REPOS, null, "Ana", "ana@az.com", null);

        long modified = commits.attributeByAuthorEmails(ORG, VcsProvider.AZURE_REPOS, List.of("ana@az.com"), USER);

        assertThat(modified).isEqualTo(1);
        assertThat(ownerOfCommit(ORG, "a")).isEqualTo(USER);
        assertThat(ownerOfCommit(ORG, "b")).isEqualTo("someone-else");
        assertThat(ownerOfCommit(ORG, "c")).isNull();
        assertThat(ownerOfCommit(OTHER_ORG, "d")).isNull();
    }

    @Test
    void pullRequests_shouldAttributeOwnerlessAndReportAffectedRepositories() {
        pullRequest(ORG, "repo-1", "1", "58421", null);
        pullRequest(ORG, "repo-2", "2", "58421", null);
        pullRequest(ORG, "repo-3", "3", "58421", "someone-else");
        pullRequest(OTHER_ORG, "repo-9", "4", "58421", null);

        VcsAttribution result = pullRequests.attributeByAuthorExternalIds(ORG, VcsProvider.GITHUB, Set.of("58421"),
                USER);

        assertThat(result.modified()).isEqualTo(2);
        assertThat(result.repositoryIds()).containsExactlyInAnyOrder("repo-1", "repo-2");
        assertThat(ownerOfPullRequest(ORG, "1")).isEqualTo(USER);
        assertThat(ownerOfPullRequest(ORG, "3")).isEqualTo("someone-else");
        assertThat(ownerOfPullRequest(OTHER_ORG, "4")).isNull();
    }

    @Test
    void pullRequests_shouldDoNothing_whenNothingMatches() {
        pullRequest(ORG, "repo-1", "1", "999", null);

        VcsAttribution result = pullRequests.attributeByAuthorExternalIds(ORG, VcsProvider.GITHUB, Set.of("58421"),
                USER);

        assertThat(result.modified()).isZero();
        assertThat(result.repositoryIds()).isEmpty();
    }

    @Test
    void reviewEvents_shouldAttributeOwnerlessAndReportAffectedRepositories() {
        event(ORG, "repo-1", "e1", "58421", null);
        event(ORG, "repo-2", "e2", "58421", "someone-else");
        event(OTHER_ORG, "repo-9", "e3", "58421", null);

        VcsAttribution result = events.attributeByActorExternalIds(ORG, VcsProvider.GITHUB, Set.of("58421"), USER);

        assertThat(result.modified()).isEqualTo(1);
        assertThat(result.repositoryIds()).containsExactly("repo-1");
        assertThat(ownerOfEvent(ORG, "e1")).isEqualTo(USER);
        assertThat(ownerOfEvent(ORG, "e2")).isEqualTo("someone-else");
        assertThat(ownerOfEvent(OTHER_ORG, "e3")).isNull();
    }

    @Test
    void shouldBeIdempotent_onSecondRun() {
        pullRequest(ORG, "repo-1", "1", "58421", null);

        pullRequests.attributeByAuthorExternalIds(ORG, VcsProvider.GITHUB, Set.of("58421"), USER);
        VcsAttribution again = pullRequests.attributeByAuthorExternalIds(ORG, VcsProvider.GITHUB, Set.of("58421"),
                USER);

        assertThat(again.modified()).isZero();
    }
}
