package andromeda.sei.ms.feature.organization.infrastructure.persistence;

import andromeda.sei.ms.shared.testsupport.MongoIntegrationTest;
import andromeda.sei.ms.feature.organization.domain.Organization;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@MongoIntegrationTest
class OrganizationRepositoryImplTest {

    @Autowired
    private OrganizationMongoRepository mongoRepository;

    private OrganizationRepositoryImpl repository;

    @BeforeEach
    void setUp() {
        var mapper = new OrganizationDocumentMapper();
        repository = new OrganizationRepositoryImpl(mongoRepository, mapper);
        mongoRepository.deleteAll();
    }

    @Test
    void shouldSaveAndReturnOrganizationWithMongoId() {
        var now = Instant.now();
        var org = new Organization(null, "Test Org", "test-org", null, null, null, null, null, true, 0L, now, now);

        var saved = repository.save(org);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getSlug()).isEqualTo("test-org");
        assertThat(saved.getName()).isEqualTo("Test Org");
        assertThat(saved.isActive()).isTrue();
    }

    @Test
    void shouldFindBySlugWhenActive() {
        var now = Instant.now();
        repository.save(new Organization(null, "Test Org", "test-org", null, null, null, null, null, true, 0L, now, now));

        var found = repository.findBySlug("test-org");

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Test Org");
    }

    @Test
    void shouldNotFindBySlugWhenInactive() {
        var doc = OrganizationDocument.builder()
                .name("Inactive Org")
                .slug("inactive-org")
                .active(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        mongoRepository.save(doc);

        assertThat(repository.findBySlug("inactive-org")).isEmpty();
    }

    @Test
    void shouldNotFindByIdWhenInactive() {
        var doc = OrganizationDocument.builder()
                .name("Inactive Org")
                .slug("inactive-id-org")
                .active(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        var saved = mongoRepository.save(doc);

        assertThat(repository.findById(saved.getId())).isEmpty();
    }

    @Test
    void shouldReturnTrueForExistingSlug() {
        var now = Instant.now();
        repository.save(new Organization(null, "Test Org", "test-org", null, null, null, null, null, true, 0L, now, now));

        assertThat(repository.existsBySlug("test-org")).isTrue();
        assertThat(repository.existsBySlug("other-slug")).isFalse();
    }
}
