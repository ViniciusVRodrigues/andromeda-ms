package andromeda.sei.ms.feature.vcs.infrastructure.migration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import static andromeda.sei.ms.feature.vcs.infrastructure.migration.VcsCoreMigrationMappings.ID;
import static andromeda.sei.ms.feature.vcs.infrastructure.migration.VcsCoreMigrationMappings.ORGANIZATION_ID;
import static andromeda.sei.ms.feature.vcs.infrastructure.migration.VcsCoreMigrationMappings.idOf;

/**
 * Migracao {@code V__vcs_core} (vcs-core-refactor D7, fase 1): copia os dados do GitHub para as
 * collections neutras com {@code provider = GITHUB}.
 *
 * <ul>
 *   <li>{@code github_repos} → {@code vcs_repositories} (id numerico como {@code externalRepoId});</li>
 *   <li>{@code github_repo_bindings} → {@code vcs_repo_bindings}: a chave {@code owner/name} vira o
 *       id interno do repositorio; sem repositorio correspondente, cria um pendente de primeiro sync
 *       (sem {@code externalRepoId}) e registra no log;</li>
 *   <li>{@code github_pull_requests} e {@code github_commits} → {@code vcs_*} (autor em {@code authorLogin});</li>
 *   <li>{@code github_reviews} → {@code vcs_review_events} (DISMISSED vira COMMENTED; PENDING fica de fora);</li>
 *   <li>{@code github_configs.workItemLinkConfig} → {@code organizations.vcsSettings.workItemLink}.</li>
 * </ul>
 *
 * <p>Idempotente: todo documento e gravado por {@code upsert} no mesmo {@code _id} da origem, so com
 * {@code $setOnInsert} — rodar de novo nao duplica nem sobrescreve o que o sync ja atualizou. As
 * collections {@code github_*} ficam intocadas por uma versao (rollback). Ordem 26: depois dos
 * seeders, do indice parcial de {@code vcs_repositories} (11) e das migracoes anteriores.
 */
@Slf4j
@Component
@Order(26)
@RequiredArgsConstructor
public class VcsCoreMigration implements ApplicationRunner {

    static final String GITHUB_REPOS = "github_repos";
    static final String GITHUB_BINDINGS = "github_repo_bindings";
    static final String GITHUB_PULL_REQUESTS = "github_pull_requests";
    static final String GITHUB_COMMITS = "github_commits";
    static final String GITHUB_REVIEWS = "github_reviews";
    static final String GITHUB_CONFIGS = "github_configs";
    static final String VCS_REPOSITORIES = "vcs_repositories";
    static final String VCS_BINDINGS = "vcs_repo_bindings";
    static final String VCS_PULL_REQUESTS = "vcs_pull_requests";
    static final String VCS_COMMITS = "vcs_commits";
    static final String VCS_REVIEW_EVENTS = "vcs_review_events";
    static final String ORGANIZATIONS = "organizations";
    static final int BATCH_SIZE = 500;

    private final MongoTemplate mongoTemplate;

    @Override
    public void run(ApplicationArguments args) {
        Map<String, Long> copied = new LinkedHashMap<>();
        copied.put(VCS_REPOSITORIES, copy(GITHUB_REPOS, VCS_REPOSITORIES, doc -> Optional.of(VcsCoreMigrationMappings.repository(doc))));
        copied.put(VCS_BINDINGS, migrateBindings());
        copied.put(VCS_PULL_REQUESTS, copy(GITHUB_PULL_REQUESTS, VCS_PULL_REQUESTS, doc -> Optional.of(VcsCoreMigrationMappings.pullRequest(doc))));
        copied.put(VCS_COMMITS, copy(GITHUB_COMMITS, VCS_COMMITS, doc -> Optional.of(VcsCoreMigrationMappings.commit(doc))));
        Map<String, String> repositoryByPullRequest = repositoryByPullRequest();
        copied.put(VCS_REVIEW_EVENTS, copy(GITHUB_REVIEWS, VCS_REVIEW_EVENTS, doc -> VcsCoreMigrationMappings.reviewEvent(
                doc, repositoryByPullRequest.get(String.valueOf(doc.get("pullRequestId"))))));
        copied.put("organizations.vcsSettings", migrateWorkItemLinkConfigs());
        log.info("VcsCoreMigration: source documents processed per target {}", copied);
    }

    // ------------------------------------------------------------------
    // Generic copy: upsert by _id with $setOnInsert only
    // ------------------------------------------------------------------

    private long copy(String source, String target, Function<Document, Optional<Document>> mapping) {
        if (!mongoTemplate.collectionExists(source)) return 0;
        List<Document> batch = new ArrayList<>(BATCH_SIZE);
        long processed = 0;
        try (Stream<Document> documents = mongoTemplate.stream(new Query(), Document.class, source)) {
            for (Document document : (Iterable<Document>) documents::iterator) {
                Optional<Document> mapped = mapping.apply(document);
                if (mapped.isEmpty()) continue;
                batch.add(mapped.get());
                processed++;
                if (batch.size() == BATCH_SIZE) flush(target, batch);
            }
        }
        flush(target, batch);
        return processed;
    }

    private void flush(String target, List<Document> batch) {
        if (batch.isEmpty()) return;
        BulkOperations bulk = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, target);
        for (Document document : batch) {
            bulk.upsert(Query.query(Criteria.where(ID).is(document.get(ID))), insertOnly(document));
        }
        try {
            bulk.execute();
        } catch (DataAccessException e) {
            // Unordered: every other document of the batch was still written. A duplicate key here
            // means the sync already created that row under another id — it wins.
            log.warn("VcsCoreMigration: some documents of {} were skipped: {}", target, e.getMessage());
        }
        batch.clear();
    }

    static Update insertOnly(Document document) {
        Update update = new Update();
        document.forEach((field, value) -> {
            if (!ID.equals(field)) update.setOnInsert(field, value);
        });
        return update;
    }

    // ------------------------------------------------------------------
    // Bindings: owner/name → internal repository id (pending when unknown)
    // ------------------------------------------------------------------

    private long migrateBindings() {
        if (!mongoTemplate.collectionExists(GITHUB_BINDINGS)) return 0;
        List<Document> bindings = new ArrayList<>();
        try (Stream<Document> documents = mongoTemplate.stream(new Query(), Document.class, GITHUB_BINDINGS)) {
            documents.forEach(binding -> bindings.add(mapBinding(binding)));
        }
        int migrated = bindings.size();
        flush(VCS_BINDINGS, bindings);
        return migrated;
    }

    private Document mapBinding(Document githubBinding) {
        String orgId = githubBinding.getString(ORGANIZATION_ID);
        String fullName = githubBinding.getString("repoId");
        Optional<String> repositoryId = migratedRepositoryId(orgId, fullName);
        if (repositoryId.isPresent()) {
            return VcsCoreMigrationMappings.binding(githubBinding, repositoryId.get(), false);
        }
        String pendingId = pendingRepositoryId(orgId, fullName);
        log.warn("VcsCoreMigration: binding {} of org={} references {} which was never synced; "
                + "repository {} created pending first sync", idOf(githubBinding), orgId, fullName, pendingId);
        return VcsCoreMigrationMappings.binding(githubBinding, pendingId, true);
    }

    /** The github_repos row carrying the full name (active first), whose id the migrated repository keeps. */
    private Optional<String> migratedRepositoryId(String orgId, String fullName) {
        List<Document> repos = mongoTemplate.find(Query.query(Criteria.where(ORGANIZATION_ID).is(orgId)
                .and("fullName").is(fullName)), Document.class, GITHUB_REPOS);
        return repos.stream()
                .sorted((a, b) -> Boolean.compare(Boolean.FALSE.equals(a.get("active")), Boolean.FALSE.equals(b.get("active"))))
                .map(VcsCoreMigrationMappings::idOf)
                .findFirst();
    }

    /** Reuses the pending repository of an earlier run (idempotency), creating it only once. */
    private String pendingRepositoryId(String orgId, String fullName) {
        Query sameName = Query.query(Criteria.where(ORGANIZATION_ID).is(orgId)
                .and(VcsCoreMigrationMappings.PROVIDER).is("GITHUB").and("displayName").is(fullName));
        Document existing = mongoTemplate.findOne(sameName, Document.class, VCS_REPOSITORIES);
        if (existing != null) return idOf(existing);
        Document pending = VcsCoreMigrationMappings.pendingRepository(orgId, fullName, new Date());
        mongoTemplate.insert(pending, VCS_REPOSITORIES);
        return idOf(pending);
    }

    // ------------------------------------------------------------------
    // Reviews need their repository: resolved through the pull request
    // ------------------------------------------------------------------

    private Map<String, String> repositoryByPullRequest() {
        Map<String, String> byPullRequest = new HashMap<>();
        if (!mongoTemplate.collectionExists(GITHUB_PULL_REQUESTS)) return byPullRequest;
        Query query = new Query();
        query.fields().include("repoId");
        try (Stream<Document> documents = mongoTemplate.stream(query, Document.class, GITHUB_PULL_REQUESTS)) {
            documents.forEach(pr -> byPullRequest.put(idOf(pr), pr.getString("repoId")));
        }
        return byPullRequest;
    }

    // ------------------------------------------------------------------
    // Work item link configuration → organization setting
    // ------------------------------------------------------------------

    private long migrateWorkItemLinkConfigs() {
        if (!mongoTemplate.collectionExists(GITHUB_CONFIGS)) return 0;
        long migrated = 0;
        Query withConfig = Query.query(Criteria.where("workItemLinkConfig").exists(true).ne(null));
        for (Document config : mongoTemplate.find(withConfig, Document.class, GITHUB_CONFIGS)) {
            Document link = config.get("workItemLinkConfig", Document.class);
            String orgId = config.getString(ORGANIZATION_ID);
            if (link == null || orgId == null) continue;
            Query organization = Query.query(Criteria.where(ID).in(organizationIds(orgId))
                    .and("vcsSettings.workItemLink").exists(false));
            Update update = new Update().set("vcsSettings.workItemLink", new Document("enabled",
                    Boolean.TRUE.equals(link.get("enabled"))).append("prefixes", link.get("prefixes")));
            migrated += mongoTemplate.updateFirst(organization, update, ORGANIZATIONS).getModifiedCount();
        }
        return migrated;
    }

    /** Organization ids are stored as ObjectId; match the string form too. */
    private static List<Object> organizationIds(String orgId) {
        return ObjectId.isValid(orgId) ? List.of(new ObjectId(orgId), orgId) : List.of(orgId);
    }
}
