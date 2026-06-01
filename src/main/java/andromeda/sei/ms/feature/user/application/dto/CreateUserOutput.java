package andromeda.sei.ms.feature.user.application.dto;

import andromeda.sei.ms.feature.user.domain.User;

import java.time.Instant;

public record CreateUserOutput(
        String id,
        String email,
        String roleId,
        String inviteToken,
        Instant createdAt
) {
    public static CreateUserOutput from(User user, String inviteToken) {
        return new CreateUserOutput(
                user.getId(),
                user.getEmail(),
                user.getRoleId(),
                inviteToken,
                user.getCreatedAt()
        );
    }
}
