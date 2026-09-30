package andromeda.sei.ms.feature.vcs.domain;

import java.util.List;
import java.util.Optional;

/** Port of {@code vcs_repo_bindings}. Every query is scoped by {@code organizationId}. */
public interface VcsRepoBindingRepository {

    Optional<VcsRepoBinding> findByIdAndOrganizationIdAndActiveTrue(String id, String organizationId);

    /** The active binding of a repository, whatever the project (exclusivity check). */
    Optional<VcsRepoBinding> findActiveByOrganizationIdAndRepositoryId(String organizationId, String repositoryId);

    List<VcsRepoBinding> findAllActiveByOrganizationId(String organizationId);

    List<VcsRepoBinding> findAllActiveByOrganizationIdAndProjectId(String organizationId, String projectId);

    List<VcsRepoBinding> findAllActiveByOrganizationIdAndProvider(String organizationId, VcsProvider provider);

    List<VcsRepoBinding> findAllSyncEnabledByOrganizationIdAndProvider(String organizationId, VcsProvider provider);

    VcsRepoBinding save(VcsRepoBinding binding);

    void saveAll(List<VcsRepoBinding> bindings);
}
