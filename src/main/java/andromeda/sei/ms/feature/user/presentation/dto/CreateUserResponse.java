package andromeda.sei.ms.feature.user.presentation.dto;

import java.time.Instant;

public record CreateUserResponse(
        String id,
        String email,
        String roleId,
        String inviteToken,
        Instant createdAt
) {
}
