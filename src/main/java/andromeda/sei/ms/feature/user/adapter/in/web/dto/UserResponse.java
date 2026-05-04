package andromeda.sei.ms.feature.user.adapter.in.web.dto;

import andromeda.sei.ms.feature.user.domain.User;
import java.time.Instant;
import java.util.UUID;

public record UserResponse(UUID id, String name, String email, UUID roleId, boolean active, Instant createdAt) {
    public static UserResponse from(User user) {
        return new UserResponse(user.id(), user.name(), user.email(), user.roleId(), user.active(), user.createdAt());
    }
}
