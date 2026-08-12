package andromeda.sei.ms.feature.github.infrastructure.persistence;

import andromeda.sei.ms.feature.github.domain.GithubRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Disabled("@DataMongoTest removed in Spring Boot 4 — needs rewrite as @SpringBootTest slice")
class GithubRepoRepositoryImplTest {

    @Autowired
    private GithubRepoMongoRepository mongoRepository;

    private GithubRepoRepositoryImpl repository;

    private static final String ORG_ID = "org-1";
    private static final String OTHER_ORG_ID = "org-2";
    private static final Instant NOW = Instant.now();

    @BeforeEach
    void setUp() {
        repository = new GithubRepoRepositoryImpl(mongoRepository, new GithubRepoDocumentMapper());
        mongoRepository.deleteAll();
    }

    @Test
    void findAllByFullNameIn_shouldReturnMatchingActiveRepos() {
        repository.save(repo("ext-1", "octo/one", true, ORG_ID));
        repository.save(repo("ext-2", "octo/two", true, ORG_ID));
        repository.save(repo("ext-3", "octo/three", true, ORG_ID));

        var results = repository.findAllByOrganizationIdAndFullNameInAndActiveTrue(
                ORG_ID, List.of("octo/one", "octo/three"));

        assertThat(results).hasSize(2);
        assertThat(results).extracting(GithubRepo::getFullName)
                .containsExactlyInAnyOrder("octo/one", "octo/three");
    }

    @Test
    void findAllByFullNameIn_shouldNotReturnInactiveRepos() {
        repository.save(repo("ext-1", "octo/one", true, ORG_ID));
        repository.save(repo("ext-2", "octo/inactive", false, ORG_ID));

        var results = repository.findAllByOrganizationIdAndFullNameInAndActiveTrue(
                ORG_ID, List.of("octo/one", "octo/inactive"));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getFullName()).isEqualTo("octo/one");
    }

    @Test
    void findAllByFullNameIn_shouldScopeByOrganization() {
        repository.save(repo("ext-1", "octo/mine", true, ORG_ID));
        repository.save(repo("ext-2", "octo/other-org", true, OTHER_ORG_ID));

        var results = repository.findAllByOrganizationIdAndFullNameInAndActiveTrue(
                ORG_ID, List.of("octo/mine", "octo/other-org"));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getFullName()).isEqualTo("octo/mine");
        assertThat(results.get(0).getOrganizationId()).isEqualTo(ORG_ID);
    }

    @Test
    void findAllByFullNameIn_shouldReturnEmpty_whenNothingMatches() {
        repository.save(repo("ext-1", "octo/one", true, ORG_ID));

        var results = repository.findAllByOrganizationIdAndFullNameInAndActiveTrue(
                ORG_ID, List.of("octo/unknown"));

        assertThat(results).isEmpty();
    }

    private GithubRepo repo(String externalId, String fullName, boolean active, String orgId) {
        String name = fullName.contains("/") ? fullName.substring(fullName.indexOf('/') + 1) : fullName;
        return new GithubRepo(null, orgId, externalId, "octo", name, fullName, NOW,
                active, NOW, NOW, null);
    }
}
