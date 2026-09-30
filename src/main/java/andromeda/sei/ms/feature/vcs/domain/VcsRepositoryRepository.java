package andromeda.sei.ms.feature.vcs.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Port of {@code vcs_repositories}. Every query is scoped by {@code organizationId}. */
public interface VcsRepositoryRepository {

    Optional<VcsRepository> findByIdAndOrganizationId(String id, String organizationId);

    Optional<VcsRepository> findByOrganizationIdAndProviderAndExternalRepoId(
            String organizationId, VcsProvider provider, String externalRepoId);

    /** Active repository of the provider carrying {@code displayName} (pending repositories included). */
    Optional<VcsRepository> findByOrganizationIdAndProviderAndDisplayName(
            String organizationId, VcsProvider provider, String displayName);

    /** Active repositories among {@code ids}. */
    List<VcsRepository> findAllByOrganizationIdAndIdIn(String organizationId, Collection<String> ids);

    /** Active repositories of the provider (the cached side of the bindable listing). */
    List<VcsRepository> findAllByOrganizationIdAndProvider(String organizationId, VcsProvider provider);

    VcsRepository save(VcsRepository repository);
}
