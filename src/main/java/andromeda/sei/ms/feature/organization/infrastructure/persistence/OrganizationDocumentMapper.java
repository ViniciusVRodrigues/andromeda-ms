package andromeda.sei.ms.feature.organization.infrastructure.persistence;

import andromeda.sei.ms.feature.organization.domain.Organization;
import org.springframework.stereotype.Component;

@Component
public class OrganizationDocumentMapper {

    public Organization toDomain(OrganizationDocument doc) {
        return new Organization(
                doc.getId(),
                doc.getName(),
                doc.getSlug(),
                doc.isActive(),
                doc.getVersion() != null ? doc.getVersion() : 0L,
                doc.getCreatedAt(),
                doc.getUpdatedAt()
        );
    }

    public OrganizationDocument toDocument(Organization org) {
        return OrganizationDocument.builder()
                .id(org.getId())
                .name(org.getName())
                .slug(org.getSlug())
                .active(org.isActive())
                .version(org.getVersion())
                .createdAt(org.getCreatedAt())
                .updatedAt(org.getUpdatedAt())
                .build();
    }
}
