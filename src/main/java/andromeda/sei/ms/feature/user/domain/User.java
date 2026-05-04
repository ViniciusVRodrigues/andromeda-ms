package andromeda.sei.ms.feature.user.domain;

import java.time.Instant;
import java.util.UUID;

public record User(
        UUID id,
        UUID organizationId,
        String email,
        String passwordHash,
        String name,
        UUID roleId,
        String githubId,
        boolean active,
        Instant createdAt
) {
    public static User create(UUID organizationId, String email, String passwordHash, String name, UUID roleId) {
        return new User(null, organizationId, email, passwordHash, name, roleId, null, true, null);
    }
}
