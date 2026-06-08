package andromeda.sei.ms.feature.workitem.infrastructure.persistence;

import andromeda.sei.ms.feature.workitem.domain.Sprint;
import org.springframework.stereotype.Component;

@Component
public class SprintDocumentMapper {

    public Sprint toDomain(SprintDocument doc) {
        return new Sprint(
                doc.getId(),
                doc.getOrganizationId(),
                doc.getProjectId(),
                doc.getSource(),
                doc.getExternalId(),
                doc.getTeamId(),
                doc.getTeamName(),
                doc.getName(),
                doc.getPath(),
                doc.getStartDate(),
                doc.getEndDate(),
                doc.getTimeFrame(),
                doc.isActive(),
                doc.getVersion(),
                doc.getCreatedAt(),
                doc.getUpdatedAt()
        );
    }

    public SprintDocument toDocument(Sprint sprint) {
        return SprintDocument.builder()
                .id(sprint.getId())
                .organizationId(sprint.getOrganizationId())
                .projectId(sprint.getProjectId())
                .source(sprint.getSource())
                .externalId(sprint.getExternalId())
                .teamId(sprint.getTeamId())
                .teamName(sprint.getTeamName())
                .name(sprint.getName())
                .path(sprint.getPath())
                .startDate(sprint.getStartDate())
                .endDate(sprint.getEndDate())
                .timeFrame(sprint.getTimeFrame())
                .active(sprint.isActive())
                .version(sprint.getVersion())
                .createdAt(sprint.getCreatedAt())
                .updatedAt(sprint.getUpdatedAt())
                .build();
    }
}
