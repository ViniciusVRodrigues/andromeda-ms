package andromeda.sei.ms.feature.role.adapter.out.persistence;

import andromeda.sei.ms.feature.role.application.port.out.RoleRepository;
import andromeda.sei.ms.feature.role.domain.Permission;
import andromeda.sei.ms.feature.role.domain.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@RequiredArgsConstructor
public class RoleRepositoryAdapter implements RoleRepository {

    private final RoleJpaRepository jpaRepository;
    private final PermissionJpaRepository permissionJpaRepository;

    @Override
    public List<Role> findAvailableForOrganization(UUID organizationId) {
        return jpaRepository.findAvailableForOrganization(organizationId).stream()
                .map(this::toDomain).toList();
    }

    @Override
    public Optional<Role> findById(UUID id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<Role> findDefaultByName(String name) {
        return jpaRepository.findByNameAndIsDefaultTrue(name).map(this::toDomain);
    }

    @Override
    public Role save(Role role) {
        RoleJpaEntity entity;
        if (role.id() != null) {
            entity = jpaRepository.findById(role.id()).orElse(new RoleJpaEntity());
        } else {
            entity = new RoleJpaEntity();
        }
        entity.setOrganizationId(role.organizationId());
        entity.setName(role.name());
        entity.setDefault(role.isDefault());
        entity.setClonedFromRoleId(role.clonedFromRoleId());

        if (role.permissions() != null) {
            List<PermissionJpaEntity> permEntities = role.permissions().stream()
                    .map(p -> permissionJpaRepository.findById(p.id()).orElseThrow())
                    .toList();
            entity.setPermissions(permEntities);
        }

        return toDomain(jpaRepository.save(entity));
    }

    @Override
    public void delete(UUID roleId) {
        jpaRepository.deleteById(roleId);
    }

    @Override
    public boolean hasAssignedUsers(UUID roleId) {
        return jpaRepository.hasAssignedUsers(roleId);
    }

    @Override
    public List<String> findPermissionCodesByRoleId(UUID roleId) {
        return jpaRepository.findPermissionCodesByRoleId(roleId);
    }

    private Role toDomain(RoleJpaEntity entity) {
        List<Permission> permissions = entity.getPermissions() == null ? List.of() :
                entity.getPermissions().stream()
                        .map(p -> new Permission(p.getId(), p.getCode(), p.getDescription(), p.getModule()))
                        .toList();
        return new Role(entity.getId(), entity.getOrganizationId(), entity.getName(),
                entity.isDefault(), entity.getClonedFromRoleId(), permissions);
    }
}
