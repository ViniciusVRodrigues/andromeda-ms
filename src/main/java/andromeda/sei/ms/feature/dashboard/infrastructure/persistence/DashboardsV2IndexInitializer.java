package andromeda.sei.ms.feature.dashboard.infrastructure.persistence;

import andromeda.sei.ms.feature.connector.infrastructure.persistence.ConnectorTokenDocument;
import andromeda.sei.ms.feature.integration.infrastructure.persistence.IntegrationSyncLogDocument;
import andromeda.sei.ms.feature.vcs.infrastructure.persistence.VcsCommitDocument;
import andromeda.sei.ms.feature.workitem.infrastructure.persistence.SprintDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Creates the indexes the organization and integrations dashboards v2 read through that
 * {@code DashboardQueryIndexInitializer} (the project dashboards' ones) does not already create
 * (dashboards-customizaveis 1.11/1.12, task 1.14). Each one has the keys of a declaration that already
 * exists on the document, non-unique and without a name (MongoDB derives the same default name the
 * declaration gets), so since fix/mongo-index-creation, where {@code DeclaredIndexInitializer} creates
 * every declaration, these are no-ops kept as a belt-and-braces for the dashboards' read budget;
 * {@code ExplicitIndexInitializersConsistencyTest} keeps them identical. Idempotent.
 *
 * <p>A failure (an index with the same keys but other options already exists) is logged and does not
 * stop the startup: these indexes are about speed, never about correctness.
 */
@Slf4j
@Component
@Order(12)
@RequiredArgsConstructor
public class DashboardsV2IndexInitializer implements ApplicationRunner {

    private static final String ORGANIZATION_ID = "organizationId";

    private final MongoTemplate mongoTemplate;

    /** Collection document class and keys (ascending unless {@code -startedAt}). */
    static final List<IndexSpec> INDEXES = List.of(
            new IndexSpec(SprintDocument.class, List.of(ORGANIZATION_ID, "projectId")),
            new IndexSpec(VcsCommitDocument.class, List.of(ORGANIZATION_ID, "repositoryId", "active", "authoredAt")),
            new IndexSpec(IntegrationSyncLogDocument.class, List.of(ORGANIZATION_ID, "integration", "-startedAt")),
            new IndexSpec(IntegrationSyncLogDocument.class, List.of(ORGANIZATION_ID, "projectId", "status", "operation")),
            new IndexSpec(ConnectorTokenDocument.class, List.of(ORGANIZATION_ID, "active")));

    @Override
    public void run(ApplicationArguments args) {
        for (IndexSpec spec : INDEXES) {
            try {
                mongoTemplate.indexOps(spec.document()).createIndex(spec.toIndex());
            } catch (DataAccessException | IllegalStateException e) {
                log.warn("DashboardsV2IndexInitializer: index {} on {} not created: {}", spec.keys(),
                        spec.document().getSimpleName(), e.getClass().getSimpleName());
            }
        }
        log.debug("DashboardsV2IndexInitializer: ensured {} indexes", INDEXES.size());
    }

    record IndexSpec(Class<?> document, List<String> keys) {

        private static final String DESCENDING = "-";

        Index toIndex() {
            Index index = new Index();
            for (String key : keys) {
                if (key.startsWith(DESCENDING)) index.on(key.substring(1), Sort.Direction.DESC);
                else index.on(key, Sort.Direction.ASC);
            }
            return index;
        }
    }
}
