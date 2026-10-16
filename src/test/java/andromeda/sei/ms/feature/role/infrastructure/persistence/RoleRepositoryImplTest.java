package andromeda.sei.ms.feature.role.infrastructure.persistence;

import andromeda.sei.ms.shared.testsupport.MongoIntegrationTest;
import andromeda.sei.ms.feature.role.domain.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@MongoIntegrationTest
class RoleRepositoryImplTest {

    @Autowired
    private RoleMongoRepository mongoRepository;

    private RoleRepositoryImpl repository;

    private static final String ORG_ID = "org-1";
    private static final String OTHER_ORG_ID = "org-2";
    private static final Instant NOW = Instant.now();

    @BeforeEach
    void setUp() {
        var mapper = new RoleDocumentMapper();
        repository = new RoleRepositoryImpl(mongoRepository, mapper);
        mongoRepository.deleteAll();
    }

    @Test
    void save_shouldPopulateId() {
        var role = new Role(null, ORG_ID, "Custom Role", "Custom Role", false, null, List.of("AZURE_READ"), true, 0L, NOW, NOW);

        var saved = repository.save(role);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getName()).isEqualTo("Custom Role");
    }

    @Test
    void findAvailableForOrganization_shouldReturnDefaultAndOrgSpecificRoles() {
        // default (null orgId) role
        mongoRepository.save(RoleDocument.builder()
                .organizationId(null).name("ADMIN").isDefault(true)
                .permissionCodes(List.of("USER_MANAGE")).active(true)
                .createdAt(NOW).updatedAt(NOW).build());

        // org-specific role
        mongoRepository.save(RoleDocument.builder()
                .organizationId(ORG_ID).name("Custom").isDefault(false)
                .permissionCodes(List.of("AZURE_READ")).active(true)
                .createdAt(NOW).updatedAt(NOW).build());

        // role belonging to another org — should not be returned
        mongoRepository.save(RoleDocument.builder()
                .organizationId(OTHER_ORG_ID).name("Other Org Role").isDefault(false)
                .permissionCodes(List.of("REPOS_READ")).active(true)
                .createdAt(NOW).updatedAt(NOW).build());

        List<Role> roles = repository.findAvailableForOrganization(ORG_ID);

        assertThat(roles).hasSize(2);
        assertThat(roles).extracting(Role::getName).containsExactlyInAnyOrder("ADMIN", "Custom");
    }

    @Test
    void findAvailableForOrganization_shouldNotReturnInactiveRoles() {
        // inactive default role
        mongoRepository.save(RoleDocument.builder()
                .organizationId(null).name("ADMIN").isDefault(true)
                .permissionCodes(List.of()).active(false)
                .createdAt(NOW).updatedAt(NOW).build());

        List<Role> roles = repository.findAvailableForOrganization(ORG_ID);

        assertThat(roles).isEmpty();
    }

    @Test
    void findById_shouldNotReturnInactiveRole() {
        var doc = mongoRepository.save(RoleDocument.builder()
                .organizationId(ORG_ID).name("Inactive Role").isDefault(false)
                .permissionCodes(List.of()).active(false)
                .createdAt(NOW).updatedAt(NOW).build());

        assertThat(repository.findById(doc.getId())).isEmpty();
    }
}
