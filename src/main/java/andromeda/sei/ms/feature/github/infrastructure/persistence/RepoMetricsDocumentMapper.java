package andromeda.sei.ms.feature.github.infrastructure.persistence;

import andromeda.sei.ms.feature.github.domain.RepoMetrics;
import org.springframework.stereotype.Component;

@Component
public class RepoMetricsDocumentMapper {

    public RepoMetrics toDomain(RepoMetricsDocument doc) {
        if (doc == null) return null;
        return new RepoMetrics(
                doc.getId(), doc.getOrganizationId(), doc.getRepoId(), doc.getPeriodStart(), doc.getPeriodEnd(),
                doc.getCfr(), doc.isEstimated(), doc.getCalculatedAt(),
                doc.isActive(), doc.getCreatedAt(), doc.getUpdatedAt(), doc.getVersion());
    }

    public RepoMetricsDocument toDocument(RepoMetrics metrics) {
        if (metrics == null) return null;
        return RepoMetricsDocument.builder()
                .id(metrics.getId())
                .organizationId(metrics.getOrganizationId())
                .repoId(metrics.getRepoId())
                .periodStart(metrics.getPeriodStart())
                .periodEnd(metrics.getPeriodEnd())
                .cfr(metrics.getCfr())
                .estimated(metrics.isEstimated())
                .calculatedAt(metrics.getCalculatedAt())
                .active(metrics.isActive())
                .createdAt(metrics.getCreatedAt())
                .updatedAt(metrics.getUpdatedAt())
                .version(metrics.getVersion())
                .build();
    }
}
