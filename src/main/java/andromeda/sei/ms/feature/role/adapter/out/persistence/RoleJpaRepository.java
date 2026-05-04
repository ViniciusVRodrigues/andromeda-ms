package andromeda.sei.ms.feature.role.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoleJpaRepository extends JpaRepository<RoleJpaEntity, UUID> {

    @Query("SELECT r FROM RoleJpaEntity r WHERE r.organizationId IS NULL OR r.organizationId = :orgId")
    List<RoleJpaEntity> findAvailableForOrganization(@Param("orgId") UUID organizationId);

    Optional<RoleJpaEntity> findByNameAndIsDefaultTrue(String name);

    @Query("SELECT COUNT(u) > 0 FROM UserJpaEntity u WHERE u.roleId = :roleId")
    boolean hasAssignedUsers(@Param("roleId") UUID roleId);

    @Query("SELECT p.code FROM RoleJpaEntity r JOIN r.permissions p WHERE r.id = :roleId")
    List<String> findPermissionCodesByRoleId(@Param("roleId") UUID roleId);
}
