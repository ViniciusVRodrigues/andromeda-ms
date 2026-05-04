package andromeda.sei.ms.feature.role.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface PermissionJpaRepository extends JpaRepository<PermissionJpaEntity, UUID> {
    List<PermissionJpaEntity> findByCodeIn(Set<String> codes);
}
