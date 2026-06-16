package andromeda.sei.ms.feature.user.infrastructure.persistence;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface UserMongoRepository extends MongoRepository<UserDocument, String> {

    Optional<UserDocument> findByIdAndOrganizationIdAndActiveTrue(String id, String organizationId);

    List<UserDocument> findByOrganizationIdAndActiveTrue(String organizationId);

    boolean existsByEmailAndActiveTrue(String email);

    Optional<UserDocument> findByEmailAndActiveTrue(String email);

    Optional<UserDocument> findByOrganizationIdAndAzureIdAndActiveTrue(String organizationId, String azureId);

    Optional<UserDocument> findByInviteToken(String inviteToken);

    boolean existsByOrganizationIdAndRoleIdAndActiveTrue(String organizationId, String roleId);
}
