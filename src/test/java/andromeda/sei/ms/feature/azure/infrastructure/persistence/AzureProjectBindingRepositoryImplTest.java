package andromeda.sei.ms.feature.azure.infrastructure.persistence;

import andromeda.sei.ms.shared.testsupport.MongoIntegrationTest;
import andromeda.sei.ms.feature.azure.domain.AzureProjectBinding;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@MongoIntegrationTest
class AzureProjectBindingRepositoryImplTest {

    @Autowired
    private AzureProjectBindingMongoRepository mongoRepository;

    private AzureProjectBindingRepositoryImpl repository;

    private static final String ORG_ID = "org-1";
    private static final Instant NOW = Instant.now();

    @BeforeEach
    void setUp() {
        repository = new AzureProjectBindingRepositoryImpl(mongoRepository, new AzureProjectBindingDocumentMapper());
        mongoRepository.deleteAll();
    }

    @Test
    void save_shouldPersistAndReturnWithId() {
        var saved = repository.save(binding("proj-1", true, true));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getProjectId()).isEqualTo("proj-1");
    }

    @Test
    void findByOrganizationIdAndProjectIdAndActiveTrue_shouldReturnActiveBinding() {
        repository.save(binding("proj-1", true, true));

        var result = repository.findByOrganizationIdAndProjectIdAndActiveTrue(ORG_ID, "proj-1");

        assertThat(result).isPresent();
    }

    @Test
    void findByOrganizationIdAndProjectIdAndActiveTrue_shouldNotReturnInactiveBinding() {
        repository.save(binding("proj-1", false, false));

        var result = repository.findByOrganizationIdAndProjectIdAndActiveTrue(ORG_ID, "proj-1");

        assertThat(result).isEmpty();
    }

    @Test
    void findAllByOrganizationIdAndSyncEnabledTrueAndActiveTrue_shouldReturnOnlySyncEnabled() {
        repository.save(binding("proj-1", true, true));
        repository.save(binding("proj-2", false, true));

        var results = repository.findAllByOrganizationIdAndSyncEnabledTrueAndActiveTrue(ORG_ID);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getProjectId()).isEqualTo("proj-1");
    }

    @Test
    void findByAzureProjectId_shouldReturnActiveBinding() {
        repository.save(binding("proj-1", true, true));

        var result = repository.findByOrganizationIdAndAzureProjectIdAndActiveTrue(ORG_ID, "azure-proj-1");

        assertThat(result).isPresent();
        assertThat(result.get().getProjectId()).isEqualTo("proj-1");
    }

    @Test
    void findByAzureProjectId_shouldNotReturnInactiveBinding() {
        repository.save(binding("proj-1", true, false));

        var result = repository.findByOrganizationIdAndAzureProjectIdAndActiveTrue(ORG_ID, "azure-proj-1");

        assertThat(result).isEmpty();
    }

    @Test
    void findByAzureProjectId_shouldScopeByOrganization() {
        repository.save(binding("proj-1", true, true));

        var result = repository.findByOrganizationIdAndAzureProjectIdAndActiveTrue("org-other", "azure-proj-1");

        assertThat(result).isEmpty();
    }

    private AzureProjectBinding binding(String projectId, boolean syncEnabled, boolean active) {
        return new AzureProjectBinding(null, ORG_ID, projectId, "azure-proj-1", "My Project",
                syncEnabled, null, null, active, NOW, NOW, null);
    }
}
