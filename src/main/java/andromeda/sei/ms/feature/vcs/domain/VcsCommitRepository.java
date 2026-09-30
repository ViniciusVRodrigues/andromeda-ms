package andromeda.sei.ms.feature.vcs.domain;

import java.time.Instant;
import java.util.Optional;

/** Port of {@code vcs_commits}. Every query is scoped by {@code organizationId}. */
public interface VcsCommitRepository {

    Optional<VcsCommit> findByOrganizationIdAndRepositoryIdAndSha(String organizationId, String repositoryId, String sha);

    /** Active commits authored within {@code [from, to)}. */
    long countByOrganizationIdAndRepositoryIdAndAuthoredAtBetween(
            String organizationId, String repositoryId, Instant from, Instant to);

    VcsCommit save(VcsCommit commit);
}
