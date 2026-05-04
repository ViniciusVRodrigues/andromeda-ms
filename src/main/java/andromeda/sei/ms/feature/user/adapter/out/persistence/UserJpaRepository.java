package andromeda.sei.ms.feature.user.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserJpaRepository extends JpaRepository<UserJpaEntity, UUID> {
    Optional<UserJpaEntity> findByIdAndOrganizationId(UUID id, UUID organizationId);
    Optional<UserJpaEntity> findByEmailAndOrganizationId(String email, UUID organizationId);
    List<UserJpaEntity> findAllByOrganizationId(UUID organizationId);
}
