package andromeda.sei.ms.feature.user.infrastructure.persistence;

import andromeda.sei.ms.shared.testsupport.MongoIntegrationTest;
import andromeda.sei.ms.feature.user.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@MongoIntegrationTest
class UserRepositoryImplTest {

    @Autowired
    private UserMongoRepository mongoRepository;

    private UserRepositoryImpl repository;

    private static final String ORG_ID = "org-1";
    private static final String OTHER_ORG_ID = "org-2";
    private static final Instant NOW = Instant.now();

    @BeforeEach
    void setUp() {
        repository = new UserRepositoryImpl(mongoRepository, new UserDocumentMapper(), null);
        mongoRepository.deleteAll();
    }

    @Test
    void save_shouldPopulateId() {
        var user = new User(null, ORG_ID, "test@example.com", "hash", "Test User", "role-dev", null, null, null, null, null, false, true, 0L, NOW, NOW);

        var saved = repository.save(user);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getEmail()).isEqualTo("test@example.com");
    }

    @Test
    void findByIdAndOrganizationId_shouldNotReturnUserFromAnotherOrg() {
        var doc = mongoRepository.save(UserDocument.builder()
                .organizationId(OTHER_ORG_ID).email("other@example.com").passwordHash("hash")
                .name("Other User").roleId("role-dev").active(true)
                .createdAt(NOW).updatedAt(NOW).build());

        assertThat(repository.findByIdAndOrganizationId(doc.getId(), ORG_ID)).isEmpty();
    }

    @Test
    void findByIdAndOrganizationId_shouldNotReturnInactiveUser() {
        var doc = mongoRepository.save(UserDocument.builder()
                .organizationId(ORG_ID).email("inactive@example.com").passwordHash("hash")
                .name("Inactive User").roleId("role-dev").active(false)
                .createdAt(NOW).updatedAt(NOW).build());

        assertThat(repository.findByIdAndOrganizationId(doc.getId(), ORG_ID)).isEmpty();
    }

    @Test
    void existsByEmailAndOrganizationId_shouldReturnTrue() {
        mongoRepository.save(UserDocument.builder()
                .organizationId(ORG_ID).email("exists@example.com").passwordHash("hash")
                .name("Exists User").roleId("role-dev").active(true)
                .createdAt(NOW).updatedAt(NOW).build());

        assertThat(repository.existsByEmail("exists@example.com")).isTrue();
        assertThat(repository.existsByEmail("other@example.com")).isFalse();
    }

    @Test
    void findAllByOrganizationId_shouldNotReturnDeactivatedUsers() {
        mongoRepository.save(UserDocument.builder()
                .organizationId(ORG_ID).email("active@example.com").passwordHash("hash")
                .name("Active").roleId("role-dev").active(true)
                .createdAt(NOW).updatedAt(NOW).build());
        mongoRepository.save(UserDocument.builder()
                .organizationId(ORG_ID).email("inactive@example.com").passwordHash("hash")
                .name("Inactive").roleId("role-dev").active(false)
                .createdAt(NOW).updatedAt(NOW).build());

        var users = repository.findAllByOrganizationId(ORG_ID);

        assertThat(users).hasSize(1);
        assertThat(users.get(0).getEmail()).isEqualTo("active@example.com");
    }
}
