package andromeda.sei.ms.feature.github.infrastructure.persistence;

import andromeda.sei.ms.feature.github.domain.RepoMetrics;
import andromeda.sei.ms.feature.github.domain.RepoMetricsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class RepoMetricsRepositoryImpl implements RepoMetricsRepository {

    private final RepoMetricsMongoRepository mongoRepository;
    private final RepoMetricsDocumentMapper mapper;
    private final MongoTemplate mongoTemplate;

    @Override
    public Optional<RepoMetrics> findByOrganizationIdAndRepoIdAndPeriodStartAndPeriodEnd(
            String organizationId, String repoId, Instant periodStart, Instant periodEnd) {
        return mongoRepository.findByOrganizationIdAndRepoIdAndPeriodStartAndPeriodEnd(
                organizationId, repoId, periodStart, periodEnd).map(mapper::toDomain);
    }

    @Override
    public RepoMetrics save(RepoMetrics metrics) {
        RepoMetricsDocument doc = mapper.toDocument(metrics);
        Instant now = Instant.now();
        doc.setUpdatedAt(now);
        if (doc.getCreatedAt() == null) {
            doc.setCreatedAt(now);
        }

        Query query = Query.query(Criteria.where("organizationId").is(metrics.getOrganizationId())
                .and("repoId").is(metrics.getRepoId())
                .and("periodStart").is(metrics.getPeriodStart())
                .and("periodEnd").is(metrics.getPeriodEnd()));

        Update update = new Update()
                .set("cfr", doc.getCfr())
                .set("estimated", doc.isEstimated())
                .set("calculatedAt", doc.getCalculatedAt())
                .set("active", doc.isActive())
                .set("updatedAt", now)
                .setOnInsert("organizationId", doc.getOrganizationId())
                .setOnInsert("repoId", doc.getRepoId())
                .setOnInsert("periodStart", doc.getPeriodStart())
                .setOnInsert("periodEnd", doc.getPeriodEnd())
                .setOnInsert("createdAt", doc.getCreatedAt() != null ? doc.getCreatedAt() : now);

        mongoTemplate.upsert(query, update, RepoMetricsDocument.class);

        return findByOrganizationIdAndRepoIdAndPeriodStartAndPeriodEnd(
                metrics.getOrganizationId(), metrics.getRepoId(), metrics.getPeriodStart(), metrics.getPeriodEnd())
                .orElseThrow();
    }
}
