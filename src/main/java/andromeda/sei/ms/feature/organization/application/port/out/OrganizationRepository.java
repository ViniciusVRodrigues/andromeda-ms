package andromeda.sei.ms.feature.organization.application.port.out;

import andromeda.sei.ms.feature.organization.domain.Organization;

import java.util.Optional;
import java.util.UUID;

public interface OrganizationRepository {
    Organization save(Organization organization);
    boolean existsBySlug(String slug);
    Optional<Organization> findById(UUID id);
}
