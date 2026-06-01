package andromeda.sei.ms.feature.organization.domain;

import java.util.Optional;

public interface OrganizationRepository {

    Optional<Organization> findById(String id);

    Optional<Organization> findBySlug(String slug);

    boolean existsBySlug(String slug);

    Organization save(Organization organization);
}
