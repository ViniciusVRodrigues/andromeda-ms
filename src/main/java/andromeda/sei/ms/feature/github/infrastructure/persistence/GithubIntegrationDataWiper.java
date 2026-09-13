package andromeda.sei.ms.feature.github.infrastructure.persistence;

import andromeda.sei.ms.feature.integration.domain.Integration;
import andromeda.sei.ms.feature.integration.domain.IntegrationDataWiper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Hard delete dos dados que o GitHub produziu numa organizacao.
 *
 * <p>Ordem: reviews -> pull requests -> repos, mais commits e metricas. Reviews referenciam PRs
 * por id e nao carregam repoId, entao no escopo por projeto os ids sao resolvidos antes.
 *
 * <p>O recorte do GitHub e por {@code repoId}; um projeto pode ter mais de um repositorio
 * vinculado, entao o escopo por projeto primeiro resolve os bindings daquele projeto.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GithubIntegrationDataWiper implements IntegrationDataWiper {

    private final MongoTemplate mongoTemplate;

    @Override
    public Integration integration() {
        return Integration.GITHUB;
    }

    @Override
    public Map<String, Long> wipe(String organizationId, WipeScope scope) {
        Map<String, Long> removidos = new LinkedHashMap<>();

        List<String> repoIds = scope.projectId() == null ? null : repoIdsDoProjeto(organizationId, scope.projectId());
        if (repoIds != null && repoIds.isEmpty()) {
            log.info("Wipe GitHub: org={} projeto={} nao tem repositorio vinculado", organizationId, scope.projectId());
            return removidos;
        }

        List<String> pullRequestIds = pullRequestIds(organizationId, repoIds);
        if (!pullRequestIds.isEmpty()) {
            Query reviews = Query.query(Criteria.where("organizationId").is(organizationId)
                    .and("pullRequestId").in(pullRequestIds));
            removidos.put("github_reviews",
                    mongoTemplate.remove(reviews, "github_reviews").getDeletedCount());
        } else {
            removidos.put("github_reviews", 0L);
        }

        removidos.put("github_pull_requests",
                mongoTemplate.remove(porRepo(organizationId, repoIds), "github_pull_requests").getDeletedCount());
        removidos.put("github_commits",
                mongoTemplate.remove(porRepo(organizationId, repoIds), "github_commits").getDeletedCount());
        removidos.put("repo_metrics",
                mongoTemplate.remove(porRepo(organizationId, repoIds), "repo_metrics").getDeletedCount());

        /* github_repos nao tem repoId: o proprio _id e o repo. */
        Criteria reposCriteria = Criteria.where("organizationId").is(organizationId);
        if (repoIds != null) reposCriteria = reposCriteria.and("_id").in(repoIds);
        removidos.put("github_repos",
                mongoTemplate.remove(Query.query(reposCriteria), "github_repos").getDeletedCount());

        log.info("Wipe GitHub concluido: org={} scope={} removidos={}", organizationId, scope, removidos);
        return removidos;
    }

    private List<String> repoIdsDoProjeto(String organizationId, String projectId) {
        Query q = Query.query(Criteria.where("organizationId").is(organizationId)
                .and("projectId").is(projectId));
        List<GithubRepoBindingDocument> bindings = mongoTemplate.find(q, GithubRepoBindingDocument.class);
        List<String> ids = new ArrayList<>(bindings.size());
        for (GithubRepoBindingDocument b : bindings) ids.add(b.getRepoId());
        return ids;
    }

    private List<String> pullRequestIds(String organizationId, List<String> repoIds) {
        Query q = porRepo(organizationId, repoIds);
        q.fields().include("_id");
        List<GithubPullRequestDocument> docs = mongoTemplate.find(q, GithubPullRequestDocument.class);
        List<String> ids = new ArrayList<>(docs.size());
        for (GithubPullRequestDocument d : docs) ids.add(d.getId());
        return ids;
    }

    private Query porRepo(String organizationId, List<String> repoIds) {
        Criteria c = Criteria.where("organizationId").is(organizationId);
        if (repoIds != null) c = c.and("repoId").in(repoIds);
        return Query.query(c);
    }
}
