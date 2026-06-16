package andromeda.sei.ms.feature.user.domain;

import java.util.List;
import java.util.Optional;

public interface UserRepository {

    Optional<User> findByIdAndOrganizationId(String id, String organizationId);

    List<User> findAllByOrganizationId(String organizationId);

    boolean existsByEmail(String email);

    Optional<User> findByEmail(String email);

    Optional<User> findByOrganizationIdAndAzureId(String organizationId, String azureId);

    Optional<User> findByInviteToken(String inviteToken);

    boolean existsByOrganizationIdAndRoleIdAndActiveTrue(String organizationId, String roleId);

    User save(User user);
}
