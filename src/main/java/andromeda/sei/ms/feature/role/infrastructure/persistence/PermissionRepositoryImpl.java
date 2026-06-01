package andromeda.sei.ms.feature.role.infrastructure.persistence;

import andromeda.sei.ms.feature.role.domain.Permission;
import andromeda.sei.ms.feature.role.domain.PermissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
@RequiredArgsConstructor
public class PermissionRepositoryImpl implements PermissionRepository {

    private final PermissionMongoRepository mongoRepository;
    private final PermissionDocumentMapper mapper;

    @Override
    public boolean existsByCode(String code) {
        return mongoRepository.existsByCode(code);
    }

    @Override
    public Permission save(Permission permission) {
        PermissionDocument doc = mapper.toDocument(permission);
        doc.setUpdatedAt(Instant.now());
        PermissionDocument saved = mongoRepository.save(doc);
        return mapper.toDomain(saved);
    }
}
