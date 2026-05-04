package andromeda.sei.ms.feature.organization.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface OrganizationJpaRepository extends JpaRepository<OrganizationJpaEntity, UUID> {
    boolean existsBySlug(String slug);
}
