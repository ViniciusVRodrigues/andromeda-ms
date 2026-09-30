package andromeda.sei.ms.feature.user.application.dto;

import andromeda.sei.ms.shared.domain.security.PermissionOverride;

import java.time.Instant;

public record PermissionOverrideOutput(String code, String effect, String projectId, String updatedBy,
                                       Instant updatedAt) {

    public static PermissionOverrideOutput from(PermissionOverride override) {
        return new PermissionOverrideOutput(override.code(), override.effect().name(), override.projectId(),
                override.updatedBy(), override.updatedAt());
    }
}
