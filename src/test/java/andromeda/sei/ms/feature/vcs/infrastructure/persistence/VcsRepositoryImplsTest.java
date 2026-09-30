package andromeda.sei.ms.feature.vcs.infrastructure.persistence;

import andromeda.sei.ms.feature.vcs.domain.PullRequestStatus;
import andromeda.sei.ms.feature.vcs.domain.ReviewKind;
import andromeda.sei.ms.feature.vcs.domain.VcsProvider;
import andromeda.sei.ms.feature.vcs.domain.VcsPullRequest;
import andromeda.sei.ms.feature.vcs.domain.VcsReviewEvent;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * vcs-core task 2.4 — the neutral persistence adapters, against a mocked {@link MongoTemplate}
 * ({@code @DataMongoTest}/embedded Mongo do not run under Boot 4 in this repo): every query is
 * tenant-scoped, the exclusivity lookup ignores inactive bindings, upserts use the unique keys and
 * the {@code $set} whitelist carries the fields a new field once silently missed.
 */
@ExtendWith(MockitoExtension.class)
class VcsRepositoryImplsTest {

    private static final String ORG = "org-1";

    @Mock private MongoTemplate mongoTemplate;

    private final VcsDocumentMapper mapper = new VcsDocumentMapper();

    @Test
    void should_lookUpActiveBindingOfRepository_forExclusivity() {
        VcsRepoBindingRepositoryImpl repository = new VcsRepoBindingRepositoryImpl(mongoTemplate, mapper);

        repository.findActiveByOrganizationIdAndRepositoryId(ORG, "repo-1");

        Document filter = captureFindOne(VcsRepoBindingDocument.class);
        assertThat(filter).containsEntry("organizationId", ORG).containsEntry("repositoryId", "repo-1")
                .containsEntry("active", true);
        assertThat(filter).doesNotContainKey("projectId");
    }

    @Test
    void should_scopeBindingListsByOrganizationAndProvider() {
        VcsRepoBindingRepositoryImpl repository = new VcsRepoBindingRepositoryImpl(mongoTemplate, mapper);

        repository.findAllSyncEnabledByOrganizationIdAndProvider(ORG, VcsProvider.GITHUB);

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(query.capture(), eq(VcsRepoBindingDocument.class));
        assertThat(query.getValue().getQueryObject()).containsEntry("organizationId", ORG)
                .containsEntry("provider", VcsProvider.GITHUB).containsEntry("syncEnabled", true)
                .containsEntry("active", true);
    }

    @Test
    void should_scopeRepositoryById_byOrganization() {
        VcsRepositoryRepositoryImpl repository = new VcsRepositoryRepositoryImpl(mongoTemplate, mapper);

        assertThat(repository.findByIdAndOrganizationId("repo-1", ORG)).isEmpty();

        assertThat(captureFindOne(VcsRepositoryDocument.class)).containsEntry("_id", "repo-1")
                .containsEntry("organizationId", ORG);
    }

    @Test
    void should_upsertPullRequest_onUniqueKey_withWhitelistedFields() {
        VcsPullRequestRepositoryImpl repository = new VcsPullRequestRepositoryImpl(mongoTemplate, mapper);
        VcsPullRequest pr = new VcsPullRequest(null, ORG, "repo-1", VcsProvider.GITHUB, "42", 42, "T", PullRequestStatus.OPEN);
        pr.setAuthorLogin("octocat");
        pr.setAuthorExternalId("58421");
        pr.setLinkedWorkItemExternalId("99");
        pr.setDraft(true);
        pr.setSourceBranch("feature/x");
        pr.setBaseBranch("main");
        pr.setLabels(List.of("bug"));
        when(mongoTemplate.findOne(any(Query.class), eq(VcsPullRequestDocument.class)))
                .thenReturn(mapper.toDocument(pr));

        repository.save(pr);

        ArgumentCaptor<Query> key = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).upsert(key.capture(), update.capture(), eq(VcsPullRequestDocument.class));
        assertThat(key.getValue().getQueryObject()).containsEntry("organizationId", ORG)
                .containsEntry("repositoryId", "repo-1").containsEntry("externalId", "42");
        Document set = (Document) update.getValue().getUpdateObject().get("$set");
        assertThat(set).containsKeys("authorLogin", "authorExternalId", "linkedWorkItemExternalId", "draft",
                "sourceBranch", "baseBranch", "labels", "status", "title");
        assertThat(set).doesNotContainKey("earliestCommitAt");
        Document onInsert = (Document) update.getValue().getUpdateObject().get("$setOnInsert");
        assertThat(onInsert).containsEntry("provider", VcsProvider.GITHUB);
    }

    @Test
    void should_upsertReviewEvent_onOrganizationPullRequestAndExternalEventId() {
        VcsReviewEventRepositoryImpl repository = new VcsReviewEventRepositoryImpl(mongoTemplate, mapper);
        VcsReviewEvent event = new VcsReviewEvent(null, ORG, "pr-1", "repo-1", VcsProvider.GITHUB, "r-1",
                ReviewKind.APPROVED, Instant.parse("2026-01-01T00:00:00Z"));
        event.setBot(true);
        when(mongoTemplate.findOne(any(Query.class), eq(VcsReviewEventDocument.class)))
                .thenReturn(mapper.toDocument(event));

        VcsReviewEvent saved = repository.save(event);

        ArgumentCaptor<Query> key = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).upsert(key.capture(), update.capture(), eq(VcsReviewEventDocument.class));
        assertThat(key.getValue().getQueryObject()).containsEntry("organizationId", ORG)
                .containsEntry("pullRequestId", "pr-1").containsEntry("externalEventId", "r-1");
        Document set = (Document) update.getValue().getUpdateObject().get("$set");
        assertThat(set).containsEntry("isBot", true).containsEntry("kind", ReviewKind.APPROVED);
        assertThat(saved.isBot()).isTrue();
    }

    @Test
    void should_countCommitsInHalfOpenWindow() {
        VcsCommitRepositoryImpl repository = new VcsCommitRepositoryImpl(mongoTemplate, mapper);
        Instant from = Instant.parse("2026-01-01T00:00:00Z");
        Instant to = Instant.parse("2026-01-02T00:00:00Z");

        repository.countByOrganizationIdAndRepositoryIdAndAuthoredAtBetween(ORG, "repo-1", from, to);

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).count(query.capture(), eq(VcsCommitDocument.class));
        Document authoredAt = (Document) query.getValue().getQueryObject().get("authoredAt");
        assertThat(authoredAt).containsEntry("$gte", from).containsEntry("$lt", to);
        assertThat(query.getValue().getQueryObject()).containsEntry("organizationId", ORG);
    }

    @Test
    void should_scopePullRequestById_byOrganization() {
        VcsPullRequestRepositoryImpl repository = new VcsPullRequestRepositoryImpl(mongoTemplate, mapper);

        assertThat(repository.findByIdAndOrganizationId("pr-1", ORG)).isEqualTo(Optional.empty());

        assertThat(captureFindOne(VcsPullRequestDocument.class)).containsEntry("_id", "pr-1")
                .containsEntry("organizationId", ORG);
    }

    @Test
    void should_createPartialUniqueIdentityIndex() {
        IndexOperations indexOps = org.mockito.Mockito.mock(IndexOperations.class);
        when(mongoTemplate.indexOps("vcs_repositories")).thenReturn(indexOps);

        new VcsIndexInitializer(mongoTemplate).run(null);

        ArgumentCaptor<Index> index = ArgumentCaptor.forClass(Index.class);
        verify(indexOps).createIndex(index.capture());
        Document options = index.getValue().getIndexOptions();
        assertThat(options).containsEntry("unique", true);
        assertThat(options.get("partialFilterExpression", Document.class)).containsKey("externalRepoId");
        assertThat(index.getValue().getIndexKeys()).containsKeys("organizationId", "provider", "externalRepoId");
    }

    private <T> Document captureFindOne(Class<T> type) {
        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).findOne(query.capture(), eq(type));
        return query.getValue().getQueryObject();
    }
}
