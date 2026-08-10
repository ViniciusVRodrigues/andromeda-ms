package andromeda.sei.ms.feature.github.domain;

import java.util.List;
import java.util.Optional;

public interface GithubRepoBindingRepository {

    Optional<GithubRepoBinding> findByOrganizationIdAndProjectIdAndRepoIdAndActiveTrue(
            String organizationId, String projectId, String repoId);

    Optional<GithubRepoBinding> findByIdAndOrganizationIdAndActiveTrue(String id, String organizationId);

    List<GithubRepoBinding> findAllByOrganizationIdAndSyncEnabledTrueAndActiveTrue(String organizationId);

    List<GithubRepoBinding> findAllByOrganizationIdAndActiveTrue(String organizationId);

    List<GithubRepoBinding> findAllByOrganizationIdAndProjectIdAndActiveTrue(String organizationId, String projectId);

    GithubRepoBinding save(GithubRepoBinding binding);
}
