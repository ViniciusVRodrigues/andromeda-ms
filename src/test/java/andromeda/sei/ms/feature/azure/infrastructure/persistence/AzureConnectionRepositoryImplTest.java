package andromeda.sei.ms.feature.azure.infrastructure.persistence;

import andromeda.sei.ms.feature.azure.domain.AzureConnection;
import andromeda.sei.ms.feature.azure.domain.AzureConnectionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@Disabled("@DataMongoTest removed in Spring Boot 4 — needs rewrite as @SpringBootTest slice")
class AzureConnectionRepositoryImplTest {

    @Autowired
    private AzureConnectionMongoRepository mongoRepository;

    private AzureConnectionRepositoryImpl repository;

    private static final String ORG_ID = "org-1";
    private static final String OTHER_ORG_ID = "org-2";
    private static final Instant NOW = Instant.now();

    @BeforeEach
    void setUp() {
        repository = new AzureConnectionRepositoryImpl(mongoRepository, new AzureConnectionDocumentMapper());
        mongoRepository.deleteAll();
    }

    @Test
    void save_shouldPersistAndReturnWithId() {
        var saved = repository.save(connection(ORG_ID, true));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getOrganizationId()).isEqualTo(ORG_ID);
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void findByOrganizationIdAndActiveTrue_shouldReturnActiveConnection() {
        repository.save(connection(ORG_ID, true));

        var result = repository.findByOrganizationIdAndActiveTrue(ORG_ID);

        assertThat(result).isPresent();
        assertThat(result.get().getStatus()).isEqualTo(AzureConnectionStatus.CONNECTED);
    }

    @Test
    void findByOrganizationIdAndActiveTrue_shouldNotReturnInactiveConnection() {
        repository.save(connection(ORG_ID, false));

        var result = repository.findByOrganizationIdAndActiveTrue(ORG_ID);

        assertThat(result).isEmpty();
    }

    @Test
    void findByOrganizationIdAndActiveTrue_shouldNotReturnConnectionFromAnotherOrg() {
        repository.save(connection(OTHER_ORG_ID, true));

        var result = repository.findByOrganizationIdAndActiveTrue(ORG_ID);

        assertThat(result).isEmpty();
    }

    private AzureConnection connection(String orgId, boolean active) {
        return new AzureConnection(null, orgId, "https://dev.azure.com/myorg",
                "enc-access", "enc-refresh", NOW.plusSeconds(3600),
                "user-1", AzureConnectionStatus.CONNECTED, null, 0, null, null,
                active, NOW, NOW, null);
    }
}
