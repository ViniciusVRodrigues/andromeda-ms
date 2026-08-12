package andromeda.sei.ms.feature.github.infrastructure.persistence;

import andromeda.sei.ms.feature.github.domain.GithubRepoBinding;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@Disabled("@DataMongoTest removed in Spring Boot 4 — needs rewrite as @SpringBootTest slice")
class GithubRepoBindingRepositoryImplTest {

    @Autowired
    private GithubRepoBindingMongoRepository mongoRepository;

    private GithubRepoBindingRepositoryImpl repository;

    private static final String ORG_ID = "org-1";
    private static final String OTHER_ORG_ID = "org-2";
    private static final Instant NOW = Instant.now();

    @BeforeEach
    void setUp() {
        repository = new GithubRepoBindingRepositoryImpl(mongoRepository, new GithubRepoBindingDocumentMapper());
        mongoRepository.deleteAll();
    }

    @Test
    void findByRepoId_shouldReturnActiveBinding() {
        repository.save(binding("proj-1", "octo/hello", true, ORG_ID));

        var result = repository.findByOrganizationIdAndRepoIdAndActiveTrue(ORG_ID, "octo/hello");

        assertThat(result).isPresent();
        assertThat(result.get().getProjectId()).isEqualTo("proj-1");
    }

    @Test
    void findByRepoId_shouldNotReturnInactiveBinding() {
        repository.save(binding("proj-1", "octo/hello", false, ORG_ID));

        var result = repository.findByOrganizationIdAndRepoIdAndActiveTrue(ORG_ID, "octo/hello");

        assertThat(result).isEmpty();
    }

    @Test
    void findByRepoId_shouldScopeByOrganization() {
        repository.save(binding("proj-1", "octo/hello", true, OTHER_ORG_ID));

        var result = repository.findByOrganizationIdAndRepoIdAndActiveTrue(ORG_ID, "octo/hello");

        assertThat(result).isEmpty();
    }

    private GithubRepoBinding binding(String projectId, String repoId, boolean active, String orgId) {
        return new GithubRepoBinding(null, orgId, projectId, repoId, true, null, active, NOW, NOW, null);
    }
}
