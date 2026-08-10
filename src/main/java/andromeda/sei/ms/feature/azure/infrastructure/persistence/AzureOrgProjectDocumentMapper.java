package andromeda.sei.ms.feature.azure.infrastructure.persistence;

import andromeda.sei.ms.feature.azure.domain.AzureOrgProject;
import org.springframework.stereotype.Component;

@Component
public class AzureOrgProjectDocumentMapper {

    public AzureOrgProject toDomain(AzureOrgProjectDocument doc) {
        return new AzureOrgProject(
                doc.getId(), doc.getOrganizationId(), doc.getAzureProjectId(), doc.getAzureProjectName(),
                doc.isActive(), doc.getCreatedAt(), doc.getUpdatedAt(), doc.getVersion());
    }

    public AzureOrgProjectDocument toDocument(AzureOrgProject project) {
        return AzureOrgProjectDocument.builder()
                .id(project.getId())
                .organizationId(project.getOrganizationId())
                .azureProjectId(project.getAzureProjectId())
                .azureProjectName(project.getAzureProjectName())
                .active(project.isActive())
                .createdAt(project.getCreatedAt())
                .updatedAt(project.getUpdatedAt())
                .version(project.getVersion())
                .build();
    }
}
