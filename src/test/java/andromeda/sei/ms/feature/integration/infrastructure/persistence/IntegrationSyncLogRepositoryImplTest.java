package andromeda.sei.ms.feature.integration.infrastructure.persistence;

import andromeda.sei.ms.feature.integration.domain.Integration;
import andromeda.sei.ms.feature.integration.domain.IntegrationSyncLog;
import andromeda.sei.ms.feature.integration.domain.SyncOperation;
import andromeda.sei.ms.feature.integration.domain.SyncStatus;
import andromeda.sei.ms.feature.integration.domain.SyncTrigger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@Disabled("@DataMongoTest removed in Spring Boot 4 — needs rewrite as @SpringBootTest slice")
class IntegrationSyncLogRepositoryImplTest {

    @Autowired
    private IntegrationSyncLogMongoRepository mongoRepository;

    private IntegrationSyncLogRepositoryImpl repository;

    private static final String ORG_ID = "org-1";
    private static final String OTHER_ORG_ID = "org-2";
    private static final Instant NOW = Instant.now();

    @BeforeEach
    void setUp() {
        repository = new IntegrationSyncLogRepositoryImpl(mongoRepository, new IntegrationSyncLogDocumentMapper());
        mongoRepository.deleteAll();
    }

    @Test
    void save_shouldPersistAndReturnWithId() {
        var log = syncLog(ORG_ID, null);

        var saved = repository.save(log);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getOrganizationId()).isEqualTo(ORG_ID);
    }

    @Test
    void findByOrganizationIdAndIntegration_shouldReturnOnlyMatchingOrg() {
        repository.save(syncLog(ORG_ID, null));
        repository.save(syncLog(OTHER_ORG_ID, null));

        var results = repository.findByOrganizationIdAndIntegration(ORG_ID, Integration.AZURE_DEVOPS, null, 100);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getOrganizationId()).isEqualTo(ORG_ID);
    }

    @Test
    void findByOrganizationIdAndIntegration_shouldFilterByProjectId() {
        repository.save(syncLog(ORG_ID, "proj-1"));
        repository.save(syncLog(ORG_ID, "proj-2"));

        var results = repository.findByOrganizationIdAndIntegration(ORG_ID, Integration.AZURE_DEVOPS, "proj-1", 100);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getProjectId()).isEqualTo("proj-1");
    }

    private IntegrationSyncLog syncLog(String orgId, String projectId) {
        return new IntegrationSyncLog(null, orgId, Integration.AZURE_DEVOPS, SyncOperation.SYNC_SPRINTS,
                SyncStatus.SUCCESS, SyncTrigger.SCHEDULED, projectId, NOW, NOW, 100L, 5, 0, null, null);
    }
}
