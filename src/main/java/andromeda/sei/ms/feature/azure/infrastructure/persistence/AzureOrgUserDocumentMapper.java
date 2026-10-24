package andromeda.sei.ms.feature.azure.infrastructure.persistence;

import andromeda.sei.ms.feature.azure.domain.AzureOrgUser;
import org.springframework.stereotype.Component;

@Component
public class AzureOrgUserDocumentMapper {

    public AzureOrgUser toDomain(AzureOrgUserDocument doc) {
        return new AzureOrgUser(
                doc.getId(), doc.getOrganizationId(), doc.getAzureId(), doc.getDisplayName(), doc.getAzureEmail(),
                doc.getPrincipalName(), doc.getSources(),
                doc.isActive(), doc.getCreatedAt(), doc.getUpdatedAt(), doc.getVersion());
    }

    public AzureOrgUserDocument toDocument(AzureOrgUser user) {
        return AzureOrgUserDocument.builder()
                .id(user.getId())
                .organizationId(user.getOrganizationId())
                .azureId(user.getAzureId())
                .displayName(user.getDisplayName())
                .azureEmail(user.getAzureEmail())
                .principalName(user.getPrincipalName())
                .sources(user.getSources())
                .active(user.isActive())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .version(user.getVersion())
                .build();
    }
}
