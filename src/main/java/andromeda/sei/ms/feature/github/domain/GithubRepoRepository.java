package andromeda.sei.ms.feature.github.domain;

import java.util.List;
import java.util.Optional;

public interface GithubRepoRepository {

    Optional<GithubRepo> findByOrganizationIdAndExternalId(String organizationId, String externalId);

    Optional<GithubRepo> findByIdAndOrganizationId(String id, String organizationId);

    List<GithubRepo> findAllByOrganizationId(String organizationId);

    GithubRepo save(GithubRepo repo);
}
