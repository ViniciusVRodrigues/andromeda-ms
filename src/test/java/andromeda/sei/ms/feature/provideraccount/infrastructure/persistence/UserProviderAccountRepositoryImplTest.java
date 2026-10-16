package andromeda.sei.ms.feature.provideraccount.infrastructure.persistence;

import andromeda.sei.ms.shared.testsupport.MongoIntegrationTest;
import andromeda.sei.ms.feature.provideraccount.domain.Provider;
import andromeda.sei.ms.feature.provideraccount.domain.UserProviderAccount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@MongoIntegrationTest
class UserProviderAccountRepositoryImplTest {

    @Autowired
    private UserProviderAccountMongoRepository mongoRepository;

    private UserProviderAccountRepositoryImpl repository;

    private static final String ORG_ID = "org-1";
    private static final String OTHER_ORG_ID = "org-2";

    @BeforeEach
    void setUp() {
        repository = new UserProviderAccountRepositoryImpl(mongoRepository, new UserProviderAccountDocumentMapper());
        mongoRepository.deleteAll();
    }

    @Test
    void shouldEnforceUniqueExternalAccountPerOrgAndProvider() {
        mongoRepository.save(document("user-1", "58421", true, ORG_ID));

        assertThatThrownBy(() -> mongoRepository.save(document("user-2", "58421", true, ORG_ID)))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void shouldEnforceUniqueUserProviderPerOrg() {
        mongoRepository.save(document("user-1", "58421", true, ORG_ID));

        assertThatThrownBy(() -> mongoRepository.save(document("user-1", "99999", true, ORG_ID)))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void shouldOnlyReturnActiveAccounts() {
        repository.save(account("user-1", "58421", true, ORG_ID));
        var inactive = account("user-2", "99999", false, ORG_ID);
        repository.save(inactive);

        var result = repository.findAllByOrganizationIdAndUserIdAndActiveTrue(ORG_ID, "user-2");

        assertThat(result).isEmpty();
    }

    @Test
    void shouldScopeQueriesByOrganization() {
        repository.save(account("user-1", "58421", true, ORG_ID));
        repository.save(account("user-1", "58421", true, OTHER_ORG_ID));

        var result = repository.findByOrganizationIdAndUserIdAndProviderAndActiveTrue(ORG_ID, "user-1", Provider.GITHUB);

        assertThat(result).isPresent();
        assertThat(result.get().getOrganizationId()).isEqualTo(ORG_ID);
    }

    private UserProviderAccountDocument document(String userId, String externalUserId, boolean active, String orgId) {
        Instant now = Instant.now();
        return UserProviderAccountDocument.builder()
                .organizationId(orgId)
                .userId(userId)
                .provider(Provider.GITHUB)
                .externalUserId(externalUserId)
                .externalLogin("login")
                .accessTokenEncrypted("enc")
                .scopes(List.of("repo"))
                .linkedAt(now)
                .active(active)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private UserProviderAccount account(String userId, String externalUserId, boolean active, String orgId) {
        Instant now = Instant.now();
        return new UserProviderAccount(null, orgId, userId, Provider.GITHUB, externalUserId,
                "login", null, "enc", null, null, List.of("repo"), now, active, null, now, now);
    }
}
