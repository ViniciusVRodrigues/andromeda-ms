package andromeda.sei.ms.feature.role.infrastructure.persistence;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface RoleMongoRepository extends MongoRepository<RoleDocument, String> {

    List<RoleDocument> findByOrganizationIdAndActiveTrue(String organizationId);

    List<RoleDocument> findByOrganizationIdIsNullAndActiveTrue();

    Optional<RoleDocument> findByNameAndOrganizationIdIsNull(String name);

    Optional<RoleDocument> findByNameAndOrganizationId(String name, String organizationId);
}
