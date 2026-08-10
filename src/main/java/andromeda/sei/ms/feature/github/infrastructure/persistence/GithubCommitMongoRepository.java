package andromeda.sei.ms.feature.github.infrastructure.persistence;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface GithubCommitMongoRepository extends MongoRepository<GithubCommitDocument, String> {

    Optional<GithubCommitDocument> findByOrganizationIdAndRepoIdAndSha(
            String organizationId, String repoId, String sha);

    List<GithubCommitDocument> findAllByOrganizationIdAndRepoId(String organizationId, String repoId);

    long countByOrganizationIdAndRepoIdAndAuthoredAtBetween(
            String organizationId, String repoId, Instant periodStart, Instant periodEnd);
}
