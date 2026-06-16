package andromeda.sei.ms.feature.project.infrastructure.persistence;

import andromeda.sei.ms.feature.project.domain.Project;
import org.springframework.stereotype.Component;

import java.util.ArrayList;

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
                doc.getMemberIds() != null ? doc.getMemberIds() : new ArrayList<>(),
                doc.getVersion(),
                doc.getCreatedAt(),
                doc.getUpdatedAt()
        );
    }

    public ProjectDocument toDocument(Project project) {
        var builder = ProjectDocument.builder()
                .organizationId(project.getOrganizationId())
                .name(project.getName())
                .slug(project.getSlug())
                .description(project.getDescription())
                .active(project.isActive())
                .memberIds(project.getMemberIds() != null ? project.getMemberIds() : new ArrayList<>())
                .createdAt(project.getCreatedAt())
                .updatedAt(project.getUpdatedAt());

        if (project.getId() != null) {
            builder.id(project.getId());
            builder.version(project.getVersion());
        }

        return builder.build();
    }
}
