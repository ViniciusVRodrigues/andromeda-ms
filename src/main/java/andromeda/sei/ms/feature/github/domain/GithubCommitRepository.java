package andromeda.sei.ms.feature.github.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface GithubCommitRepository {

    Optional<GithubCommit> findByOrganizationIdAndRepoIdAndSha(String organizationId, String repoId, String sha);

    List<GithubCommit> findAllByOrganizationIdAndRepoId(String organizationId, String repoId);

    long countByOrganizationIdAndRepoIdAndAuthoredAtBetween(
            String organizationId, String repoId, Instant periodStart, Instant periodEnd);

    GithubCommit save(GithubCommit commit);
}
