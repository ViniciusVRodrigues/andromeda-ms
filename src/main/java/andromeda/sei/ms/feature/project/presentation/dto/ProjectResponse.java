package andromeda.sei.ms.feature.project.presentation.dto;

import andromeda.sei.ms.feature.project.domain.Project;

import java.time.Instant;
import java.util.List;

public record ProjectResponse(
        String id,
        String organizationId,
        String name,
        String slug,
        String description,
        boolean active,
        List<String> memberIds,
        Instant createdAt
) {
    public static ProjectResponse from(Project project) {
        return new ProjectResponse(
                project.getId(),
                project.getOrganizationId(),
                project.getName(),
                project.getSlug(),
                project.getDescription(),
                project.isActive(),
                project.getMemberIds(),
                project.getCreatedAt()
        );
    }
}
