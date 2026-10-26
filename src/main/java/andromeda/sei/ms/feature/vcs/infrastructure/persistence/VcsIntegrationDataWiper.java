package andromeda.sei.ms.feature.vcs.infrastructure.persistence;

import andromeda.sei.ms.feature.integration.domain.Integration;
import andromeda.sei.ms.feature.integration.domain.IntegrationDataWiper;
import andromeda.sei.ms.feature.vcs.application.VcsIntegrations;
import andromeda.sei.ms.feature.vcs.domain.VcsProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Objects;

/**
 * Hard delete of the version-control data one provider produced in an organization (vcs-core
 * "Provider-scoped data wipe"), optionally limited to a project. The project's repositories are
 * resolved through its <em>active</em> bindings' internal {@code repositoryId} — no name bridge.
 *
 * <p>Order: review events → pull requests → commits → repositories. Bindings are configuration and
 * survive: each wiped repository that an active binding still references is re-created as a stub
 * pending first sync (same id, display name only) and those bindings' watermark is cleared, so the
 * next sync rebuilds the data within the backfill window instead of failing on a dangling id.
 *
 * <p>One instance per provider ({@code VcsDataWiperConfig}). Every query carries
 * {@code provider}, so the Azure Repos wiper — registered under {@code AZURE_DEVOPS} next to the
 * Boards wiper — never touches GitHub rows, and the Boards collections are not its business.
 */
@Slf4j
public class VcsIntegrationDataWiper implements IntegrationDataWiper {

    private static final String ORGANIZATION_ID = "organizationId";
    private static final String PROVIDER_FIELD = "provider";
    private static final String REPOSITORY_ID = "repositoryId";

    private final MongoTemplate mongoTemplate;
    private final VcsProvider provider;

    public VcsIntegrationDataWiper(MongoTemplate mongoTemplate, VcsProvider provider) {
        this.mongoTemplate = mongoTemplate;
        this.provider = provider;
    }

    public VcsProvider provider() {
        return provider;
    }

    @Override
    public Integration integration() {
        return VcsIntegrations.integrationOf(provider);
    }

    @Override
    public Set<String> collections() {
        return Set.of(VcsReviewEventDocument.COLLECTION, VcsPullRequestDocument.COLLECTION,
                VcsCommitDocument.COLLECTION, VcsRepositoryDocument.COLLECTION);
    }

    @Override
    public Map<String, Long> wipe(String organizationId, WipeScope scope) {
        Map<String, Long> removed = new LinkedHashMap<>();
        List<String> repositoryIds = scope.projectId() == null ? null : repositoriesOfProject(organizationId, scope.projectId());
        if (repositoryIds != null && repositoryIds.isEmpty()) {
            log.info("VCS wipe: org={} project={} has no active {} binding", organizationId, scope.projectId(), provider);
            return removed;
        }

        List<VcsRepositoryDocument> repositories = mongoTemplate.find(
                Query.query(byRepository(organizationId, repositoryIds, "_id")), VcsRepositoryDocument.class);

        removed.put(VcsReviewEventDocument.COLLECTION, remove(organizationId, repositoryIds, VcsReviewEventDocument.COLLECTION));
        removed.put(VcsPullRequestDocument.COLLECTION, remove(organizationId, repositoryIds, VcsPullRequestDocument.COLLECTION));
        removed.put(VcsCommitDocument.COLLECTION, remove(organizationId, repositoryIds, VcsCommitDocument.COLLECTION));
        removed.put(VcsRepositoryDocument.COLLECTION, mongoTemplate.remove(
                Query.query(byRepository(organizationId, repositoryIds, "_id")),
                VcsRepositoryDocument.COLLECTION).getDeletedCount());

        restoreBoundStubs(organizationId, repositories);
        log.info("VCS wipe done: org={} provider={} scope={} removed={}", organizationId, provider, scope, removed);
        return removed;
    }

    private long remove(String organizationId, List<String> repositoryIds, String collection) {
        return mongoTemplate.remove(Query.query(byRepository(organizationId, repositoryIds, REPOSITORY_ID)), collection)
                .getDeletedCount();
    }

    private Criteria byRepository(String organizationId, List<String> repositoryIds, String repositoryField) {
        Criteria criteria = Criteria.where(ORGANIZATION_ID).is(organizationId).and(PROVIDER_FIELD).is(provider);
        return repositoryIds == null ? criteria : criteria.and(repositoryField).in(repositoryIds);
    }

    private List<String> repositoriesOfProject(String organizationId, String projectId) {
        return activeBindings(Criteria.where(ORGANIZATION_ID).is(organizationId)
                .and("projectId").is(projectId)).stream()
                .map(VcsRepoBindingDocument::getRepositoryId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private List<VcsRepoBindingDocument> activeBindings(Criteria criteria) {
        return mongoTemplate.find(Query.query(criteria.and(PROVIDER_FIELD).is(provider).and("active").is(true)),
                VcsRepoBindingDocument.class);
    }

    /**
     * A GitHub stub keeps only its display name ({@code owner/name} resolves it again). An Azure
     * Repos stub keeps its GUIDs: repository names repeat across ADO projects, so a name-only stub
     * could not be resolved unambiguously on the next sync.
     */
    private VcsRepositoryDocument stubOf(VcsRepositoryDocument repo, String organizationId, Instant now) {
        VcsRepositoryDocument.VcsRepositoryDocumentBuilder stub = VcsRepositoryDocument.builder()
                .id(repo.getId()).organizationId(organizationId).provider(repo.getProvider())
                .displayName(repo.getDisplayName()).active(true).createdAt(now).updatedAt(now);
        if (repo.getProvider() == VcsProvider.AZURE_REPOS) {
            stub.externalRepoId(repo.getExternalRepoId()).externalProjectId(repo.getExternalProjectId());
        }
        return stub.build();
    }

    /** Re-creates, as pending stubs, the wiped repositories still referenced by an active binding. */
    private void restoreBoundStubs(String organizationId, List<VcsRepositoryDocument> wiped) {
        if (wiped.isEmpty()) return;
        List<String> wipedIds = wiped.stream().map(VcsRepositoryDocument::getId).toList();
        List<VcsRepoBindingDocument> bindings = activeBindings(Criteria.where(ORGANIZATION_ID).is(organizationId)
                .and(REPOSITORY_ID).in(wipedIds));
        List<String> boundIds = bindings.stream().map(VcsRepoBindingDocument::getRepositoryId).distinct().toList();
        Instant now = Instant.now();
        wiped.stream().filter(repo -> boundIds.contains(repo.getId())).forEach(repo ->
                mongoTemplate.insert(stubOf(repo, organizationId, now)));
        if (!boundIds.isEmpty()) {
            mongoTemplate.updateMulti(Query.query(Criteria.where(ORGANIZATION_ID).is(organizationId)
                            .and(REPOSITORY_ID).in(boundIds).and("active").is(true)),
                    new Update().unset("lastSyncAt").set("updatedAt", now), VcsRepoBindingDocument.class);
        }
    }
}
