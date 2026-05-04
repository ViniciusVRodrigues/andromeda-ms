package andromeda.sei.ms.feature.role.adapter.out.persistence;

import andromeda.sei.ms.feature.role.application.port.out.PermissionRepository;
import andromeda.sei.ms.feature.role.domain.Permission;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class PermissionRepositoryAdapter implements PermissionRepository {

    private final PermissionJpaRepository jpaRepository;

    @Override
    public List<Permission> findByCodes(Set<String> codes) {
        return jpaRepository.findByCodeIn(codes).stream()
                .map(e -> new Permission(e.getId(), e.getCode(), e.getDescription(), e.getModule()))
                .toList();
    }
}
