package andromeda.sei.ms.feature.connector.infrastructure.persistence;

import andromeda.sei.ms.feature.connector.domain.FluencyScore;
import andromeda.sei.ms.feature.connector.domain.FluencyScoreCategory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class FluencyScoreDocumentMapper {

    public FluencyScoreDocument toDocument(FluencyScore score) {
        var builder = FluencyScoreDocument.builder()
                .organizationId(score.getOrganizationId())
                .userId(score.getUserId())
                .overallStage(score.getOverallStage())
                .overallLabel(score.getOverallLabel())
                .computedAt(score.getComputedAt())
                .categories(toCategoryDocuments(score.getCategories()))
                .active(score.isActive())
                .createdAt(score.getCreatedAt())
                .updatedAt(score.getUpdatedAt());

        if (score.getId() != null) {
            builder.id(score.getId());
            builder.version(score.getVersion());
        }

        return builder.build();
    }

    public FluencyScore toDomain(FluencyScoreDocument doc) {
        return new FluencyScore(
                doc.getId(),
                doc.getOrganizationId(),
                doc.getUserId(),
                doc.getOverallStage(),
                doc.getOverallLabel(),
                doc.getComputedAt(),
                toCategoryDomain(doc.getCategories()),
                doc.isActive(),
                doc.getVersion(),
                doc.getCreatedAt(),
                doc.getUpdatedAt()
        );
    }

    private List<FluencyScoreCategoryDocument> toCategoryDocuments(List<FluencyScoreCategory> categories) {
        if (categories == null) return List.of();
        return categories.stream()
                .map(c -> FluencyScoreCategoryDocument.builder()
                        .category(c.getCategory())
                        .icon(c.getIcon())
                        .stage(c.getStage())
                        .tips(c.getTips())
                        .build())
                .toList();
    }

    private List<FluencyScoreCategory> toCategoryDomain(List<FluencyScoreCategoryDocument> docs) {
        if (docs == null) return List.of();
        return docs.stream()
                .map(d -> new FluencyScoreCategory(d.getCategory(), d.getIcon(), d.getStage(), d.getTips()))
                .toList();
    }
}
