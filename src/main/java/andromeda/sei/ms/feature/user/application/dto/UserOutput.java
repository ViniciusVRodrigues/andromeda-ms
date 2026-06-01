package andromeda.sei.ms.feature.user.application.dto;

import andromeda.sei.ms.feature.user.domain.User;

import java.time.Instant;

public record UserOutput(
        String id,
        String email,
        String name,
        String roleId,
        boolean active,
        Instant createdAt
) {
    public static UserOutput from(User user) {
        return new UserOutput(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getRoleId(),
                user.isActive(),
                user.getCreatedAt()
        );
    }
}
