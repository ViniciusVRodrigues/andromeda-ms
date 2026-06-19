package andromeda.sei.ms.feature.organization.infrastructure.persistence;

import andromeda.sei.ms.feature.organization.domain.AiPriceEntry;
import andromeda.sei.ms.feature.organization.domain.Organization;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OrganizationDocumentMapper {

    public Organization toDomain(OrganizationDocument doc) {
        List<AiPriceEntry> aiPrices = doc.getAiPrices() == null ? null :
                doc.getAiPrices().stream()
                        .map(e -> new AiPriceEntry(e.getModel(), e.getPricePerKInput(), e.getPricePerKOutput()))
                        .toList();

        return new Organization(
                doc.getId(),
                doc.getName(),
                doc.getSlug(),
                doc.getSector(),
                doc.getCountry(),
                doc.getSeniorities(),
                aiPrices,
                doc.getSubscriptionCostPerUserUsd(),
                doc.isActive(),
                doc.getVersion(),
                doc.getCreatedAt(),
                doc.getUpdatedAt()
        );
    }

    public OrganizationDocument toDocument(Organization org) {
        List<AiPriceEntryDocument> aiPriceDocs = org.getAiPrices() == null ? null :
                org.getAiPrices().stream()
                        .map(e -> new AiPriceEntryDocument(e.getModel(), e.getPricePerKInput(), e.getPricePerKOutput()))
                        .toList();

        var builder = OrganizationDocument.builder()
                .name(org.getName())
                .slug(org.getSlug())
                .sector(org.getSector())
                .country(org.getCountry())
                .seniorities(org.getSeniorities())
                .aiPrices(aiPriceDocs)
                .subscriptionCostPerUserUsd(org.getSubscriptionCostPerUserUsd())
                .active(org.isActive())
                .createdAt(org.getCreatedAt())
                .updatedAt(org.getUpdatedAt());

        if (org.getId() != null) {
            builder.id(org.getId());
            builder.version(org.getVersion());
        }

        return builder.build();
    }
}
