package andromeda.sei.ms.feature.role.infrastructure.persistence;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface PermissionMongoRepository extends MongoRepository<PermissionDocument, String> {

    boolean existsByCode(String code);
}
