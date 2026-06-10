package andromeda.sei.ms.feature.connector.infrastructure.persistence;

import andromeda.sei.ms.feature.connector.domain.ConnectorToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Disabled("@DataMongoTest removed in Spring Boot 4 — needs rewrite as @SpringBootTest slice")
class ConnectorTokenRepositoryImplTest {

    @Autowired
    private ConnectorTokenMongoRepository mongoRepository;

    private ConnectorTokenRepositoryImpl repository;

    @BeforeEach
    void setUp() {
        repository = new ConnectorTokenRepositoryImpl(mongoRepository, new ConnectorTokenDocumentMapper());
        mongoRepository.deleteAll();
    }

    @Test
    void shouldFindActiveTokensByOrganizationAndUser() {
        saveDocument("org-1", "user-1", "hash1", true);
        saveDocument("org-1", "user-1", "hash2", false); // revoked
        saveDocument("org-1", "user-2", "hash3", true);  // different user

        List<ConnectorToken> result = repository.findActiveByOrganizationIdAndUserId("org-1", "user-1");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTokenHash()).isEqualTo("hash1");
    }

    @Test
    void shouldFindByOrganizationIdAndTokenHash() {
        saveDocument("org-1", "user-1", "hash1", true);

        Optional<ConnectorToken> result = repository.findByOrganizationIdAndTokenHash("org-1", "hash1");

        assertThat(result).isPresent();
        assertThat(result.get().getUserId()).isEqualTo("user-1");
    }

    @Test
    void shouldNotReturnTokenFromDifferentOrganization() {
        saveDocument("org-2", "user-1", "hash1", true);

        Optional<ConnectorToken> result = repository.findByOrganizationIdAndTokenHash("org-1", "hash1");

        assertThat(result).isEmpty();
    }

    @Test
    void shouldSaveAndReturnToken() {
        Instant now = Instant.now();
        ConnectorToken token = new ConnectorToken(null, "org-1", "user-1",
                "token-abcdefgh", "hash1", true, 0L, now, now);

        ConnectorToken saved = repository.save(token);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getOrganizationId()).isEqualTo("org-1");
    }

    @Test
    void shouldFindByIdAndOrganizationId() {
        ConnectorTokenDocument doc = saveDocument("org-1", "user-1", "hash1", true);

        Optional<ConnectorToken> result = repository.findByIdAndOrganizationId(doc.getId(), "org-1");

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(doc.getId());
    }

    private ConnectorTokenDocument saveDocument(String orgId, String userId,
                                                String tokenHash, boolean active) {
        Instant now = Instant.now();
        ConnectorTokenDocument doc = ConnectorTokenDocument.builder()
                .organizationId(orgId)
                .userId(userId)
                .label("token-" + tokenHash.substring(0, Math.min(8, tokenHash.length())))
                .tokenHash(tokenHash)
                .active(active)
                .createdAt(now)
                .updatedAt(now)
                .build();
        return mongoRepository.save(doc);
    }
}
