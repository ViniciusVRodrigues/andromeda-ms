package andromeda.sei.ms.feature.integration.infrastructure.persistence;

import andromeda.sei.ms.feature.integration.domain.Integration;
import andromeda.sei.ms.feature.integration.domain.SyncOperation;
import andromeda.sei.ms.feature.integration.domain.SyncStatus;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

/**
 * dashboards-customizaveis 1.11/1.12 — the sync-log queries of the v2 dashboards (no Mongo here,
 * see {@code AzureIntegrationDataWiperTest}): always scoped by the organization, the start inclusive
 * ({@code $gte}, not the exclusive derived {@code Between}), newest first; the running check filters
 * by the projects only when given.
 */
@ExtendWith(MockitoExtension.class)
class IntegrationSyncLogDashboardQueriesTest {

    private static final String ORG = "org-1";
    private static final Instant FROM = Instant.parse("2026-09-28T00:00:00Z");

    @Mock private IntegrationSyncLogMongoRepository mongoRepository;
    @Mock private MongoTemplate mongoTemplate;

    private IntegrationSyncLogRepositoryImpl repository;

    @BeforeEach
    void setUp() {
        repository = new IntegrationSyncLogRepositoryImpl(mongoRepository, new IntegrationSyncLogDocumentMapper(),
                mongoTemplate);
    }

    @Test
    void should_readOneIntegrationSinceTheStart_inclusive_newestFirst() {
        repository.findByOrganizationIdAndIntegrationAndOperationInAndStartedAtFrom(ORG, Integration.GITHUB,
                List.of(SyncOperation.SYNC_REPOS), FROM);

        ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(captor.capture(), eq(IntegrationSyncLogDocument.class));
        Document filter = captor.getValue().getQueryObject();
        assertThat(filter.get("organizationId")).isEqualTo(ORG);
        assertThat(filter.get("integration")).isEqualTo(Integration.GITHUB);
        assertThat(filter.get("startedAt")).isEqualTo(new Document("$gte", FROM));
        assertThat(filter.get("operation")).isEqualTo(new Document("$in", List.of(SyncOperation.SYNC_REPOS)));
        assertThat(captor.getValue().getSortObject()).isEqualTo(new Document("startedAt", -1));
    }

    @Test
    void should_checkRunningLogsOfTheGivenProjects() {
        repository.existsRunningByOrganizationIdAndProjectIdInAndOperationIn(ORG, List.of("p1", "p2"),
                List.of(SyncOperation.PROJECT_REFRESH));

        ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).exists(captor.capture(), any(Class.class));
        Document filter = captor.getValue().getQueryObject();
        assertThat(filter.get("organizationId")).isEqualTo(ORG);
        assertThat(filter.get("status")).isEqualTo(SyncStatus.RUNNING);
        assertThat(filter.get("projectId")).isEqualTo(new Document("$in", List.of("p1", "p2")));
    }

    @Test
    void should_checkRunningLogsOfTheWholeOrganization_whenNoProjectIsGiven() {
        repository.existsRunningByOrganizationIdAndProjectIdInAndOperationIn(ORG, null,
                List.of(SyncOperation.PROJECT_REFRESH));

        ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).exists(captor.capture(), any(Class.class));
        assertThat(captor.getValue().getQueryObject().get("organizationId")).isEqualTo(ORG);
        assertThat(captor.getValue().getQueryObject().containsKey("projectId")).isFalse();
    }
}
