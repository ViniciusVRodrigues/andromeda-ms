package andromeda.sei.ms.feature.github.infrastructure.persistence;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.Optional;

public interface RepoMetricsMongoRepository extends MongoRepository<RepoMetricsDocument, String> {

    Optional<RepoMetricsDocument> findByOrganizationIdAndRepoIdAndPeriodStartAndPeriodEnd(
            String organizationId, String repoId, Instant periodStart, Instant periodEnd);
}
