package andromeda.sei.ms.feature.user.presentation.dto;

import java.time.Instant;

public record UserResponse(
        String id,
        String email,
        String name,
        String roleId,
        String seniority,
        boolean active,
        Instant createdAt,
        String azureId,
        String azureEmail
) {
}
