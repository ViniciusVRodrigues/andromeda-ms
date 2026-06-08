package andromeda.sei.ms.feature.project.infrastructure.persistence;

import andromeda.sei.ms.feature.project.domain.Project;
import org.springframework.stereotype.Component;

@Component
public class ProjectDocumentMapper {

    public Project toDomain(ProjectDocument doc) {
        return new Project(
                doc.getId(),
                doc.getOrganizationId(),
                doc.getName(),
                doc.getSlug(),
                doc.getDescription(),
                doc.isActive(),
                doc.getVersion() != null ? doc.getVersion() : 0L,
                doc.getCreatedAt(),
                doc.getUpdatedAt()
        );
    }

    public ProjectDocument toDocument(Project project) {
        return ProjectDocument.builder()
                .id(project.getId())
                .organizationId(project.getOrganizationId())
                .name(project.getName())
                .slug(project.getSlug())
                .description(project.getDescription())
                .active(project.isActive())
                .version(project.getVersion())
                .createdAt(project.getCreatedAt())
                .updatedAt(project.getUpdatedAt())
                .build();
    }
}
