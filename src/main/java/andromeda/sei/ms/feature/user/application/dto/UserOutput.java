package andromeda.sei.ms.feature.user.application.dto;

import andromeda.sei.ms.feature.user.domain.User;

import java.time.Instant;

public record UserOutput(
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
    public static UserOutput from(User user) {
        return new UserOutput(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getRoleId(),
                user.getSeniority(),
                user.isActive(),
                user.getCreatedAt(),
                user.getAzureId(),
                user.getAzureEmail()
        );
    }
}
