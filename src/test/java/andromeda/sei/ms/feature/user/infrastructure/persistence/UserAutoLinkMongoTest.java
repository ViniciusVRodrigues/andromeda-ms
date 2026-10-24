package andromeda.sei.ms.feature.user.infrastructure.persistence;

import andromeda.sei.ms.feature.azure.domain.AzureOrgUser;
import andromeda.sei.ms.feature.azure.domain.AzureOrgUserSource;
import andromeda.sei.ms.feature.azure.infrastructure.persistence.AzureOrgUserDocumentMapper;
import andromeda.sei.ms.feature.azure.infrastructure.persistence.AzureOrgUserMongoRepository;
import andromeda.sei.ms.feature.azure.infrastructure.persistence.AzureOrgUserRepositoryImpl;
import andromeda.sei.ms.feature.user.domain.AzureIdentitySource;
import andromeda.sei.ms.feature.user.domain.User;
import andromeda.sei.ms.shared.testsupport.MongoIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Real Mongo (Testcontainers): the conditional auto-link write and the org scoping of the directory reads. */
@MongoIntegrationTest
class UserAutoLinkMongoTest {

    private static final Instant NOW = Instant.parse("2026-10-24T03:00:00Z");
    private static final Instant LATER = Instant.parse("2026-10-24T04:00:00Z");

    @Autowired private UserMongoRepository mongoRepository;
    @Autowired private MongoTemplate mongoTemplate;
    @Autowired private AzureOrgUserMongoRepository azureOrgUserMongoRepository;

    private UserRepositoryImpl repository;

    @BeforeEach
    void setUp() {
        repository = new UserRepositoryImpl(mongoRepository, new UserDocumentMapper(), mongoTemplate);
        mongoRepository.deleteAll();
        azureOrgUserMongoRepository.deleteAll();
    }

    private User saveUser(String org, String email, String azureId, AzureIdentitySource source) {
        User u = new User(null, org, email, "h", "N", "role", null, null, null, null, null, true, true, null, NOW, NOW);
        u.setAzureId(azureId);
        u.setAzureIdentitySource(source);
        return repository.save(u);
    }

    @Test
    void linkAutoEmailIfUnlinked_shouldLinkUnlinkedUser_withAutoEmailSourceAndDate() {
        User u = saveUser("org-1", "ana@x.com", null, null);

        boolean linked = repository.linkAutoEmailIfUnlinked("org-1", u.getId(), "az-1", "ana@x.com", LATER);

        assertThat(linked).isTrue();
        User after = repository.findByIdAndOrganizationId(u.getId(), "org-1").orElseThrow();
        assertThat(after.getAzureId()).isEqualTo("az-1");
        assertThat(after.getAzureEmail()).isEqualTo("ana@x.com");
        assertThat(after.getAzureIdentitySource()).isEqualTo(AzureIdentitySource.AUTO_EMAIL);
        assertThat(after.getAzureIdentityLinkedAt()).isEqualTo(LATER);
    }

    @Test
    void linkAutoEmailIfUnlinked_shouldNotOverwriteAdminLink() {
        User u = saveUser("org-1", "ana@x.com", "az-admin", AzureIdentitySource.ADMIN);

        boolean linked = repository.linkAutoEmailIfUnlinked("org-1", u.getId(), "az-other", "ana@x.com", LATER);

        assertThat(linked).isFalse();
        User after = repository.findByIdAndOrganizationId(u.getId(), "org-1").orElseThrow();
        assertThat(after.getAzureId()).isEqualTo("az-admin");
        assertThat(after.getAzureIdentitySource()).isEqualTo(AzureIdentitySource.ADMIN);
        assertThat(after.getAzureIdentityLinkedAt()).isNull();
    }

    @Test
    void linkAutoEmailIfUnlinked_shouldNotOverwriteAnyExistingAzureId_evenWithoutSource() {
        User u = saveUser("org-1", "ana@x.com", "az-legacy", null);

        assertThat(repository.linkAutoEmailIfUnlinked("org-1", u.getId(), "az-other", "ana@x.com", LATER)).isFalse();
        assertThat(repository.findByIdAndOrganizationId(u.getId(), "org-1").orElseThrow().getAzureId())
                .isEqualTo("az-legacy");
    }

    @Test
    void linkAutoEmailIfUnlinked_shouldFilterByOrganization() {
        User u = saveUser("org-1", "ana@x.com", null, null);

        boolean linked = repository.linkAutoEmailIfUnlinked("org-2", u.getId(), "az-1", "ana@x.com", LATER);

        assertThat(linked).isFalse();
        User after = repository.findByIdAndOrganizationId(u.getId(), "org-1").orElseThrow();
        assertThat(after.getAzureId()).isNull();
        assertThat(after.getAzureIdentitySource()).isNull();
    }

    @Test
    void findAllByOrganizationId_shouldReturnOnlyTheOrgsUsers() {
        saveUser("org-1", "a@x.com", "az-1", AzureIdentitySource.ADMIN);
        saveUser("org-2", "b@x.com", "az-2", AzureIdentitySource.ADMIN);

        assertThat(repository.findAllByOrganizationId("org-1")).extracting(User::getAzureId).containsExactly("az-1");
    }

    @Test
    void azureOrgUserRepository_shouldReturnOnlyTheOrgsActiveIdentities() {
        AzureOrgUserRepositoryImpl azureRepo =
                new AzureOrgUserRepositoryImpl(azureOrgUserMongoRepository, new AzureOrgUserDocumentMapper());
        azureRepo.save(identity("org-1", "az-1", true));
        azureRepo.save(identity("org-1", "az-inactive", false));
        azureRepo.save(identity("org-2", "az-2", true));

        List<AzureOrgUser> result = azureRepo.findAllByOrganizationIdAndActiveTrue("org-1");

        assertThat(result).extracting(AzureOrgUser::getAzureId).containsExactly("az-1");
    }

    private static AzureOrgUser identity(String org, String azureId, boolean active) {
        return new AzureOrgUser(null, org, azureId, "N", "n@x.com", "n@x.com",
                List.of(AzureOrgUserSource.DIRECTORY), active, NOW, NOW, null);
    }
}
