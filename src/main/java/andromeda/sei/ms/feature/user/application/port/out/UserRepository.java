package andromeda.sei.ms.feature.user.application.port.out;

import andromeda.sei.ms.feature.user.domain.User;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository {
    User save(User user);
    Optional<User> findByIdAndOrganizationId(UUID id, UUID organizationId);
    Optional<User> findByEmailAndOrganizationId(String email, UUID organizationId);
    List<User> findAllByOrganizationId(UUID organizationId);
}
