package andromeda.sei.ms.feature.user.infrastructure.persistence;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** Embedded in {@code users.permissionOverrides} (project-scoped-permissions D2). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PermissionOverrideDocument {

    private String code;
    /** {@code GRANT} or {@code DENY}. */
    private String effect;
    /** {@code null} for an organization-level override. */
    private String projectId;
    private String updatedBy;
    private Instant updatedAt;
}
