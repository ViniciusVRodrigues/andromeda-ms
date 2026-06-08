package andromeda.sei.ms.feature.project.application.dto;

public record CreateProjectInput(
        String organizationId,
        String name,
        String description
) {
}
