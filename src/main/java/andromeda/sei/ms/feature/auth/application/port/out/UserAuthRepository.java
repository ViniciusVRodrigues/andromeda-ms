package andromeda.sei.ms.feature.auth.application.port.out;

import andromeda.sei.ms.feature.user.domain.User;

import java.util.Optional;
import java.util.UUID;

public interface UserAuthRepository {
    Optional<User> findByEmail(String email);
    void updateGithubId(UUID userId, String githubId);
}
