package andromeda.sei.ms.feature.role.domain;

public interface PermissionRepository {

    boolean existsByCode(String code);

    Permission save(Permission permission);
}
