package andromeda.sei.ms.feature.vcs.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Port of {@code vcs_commits}. Every query is scoped by {@code organizationId}. */
public interface VcsCommitRepository {

    Optional<VcsCommit> findByOrganizationIdAndRepositoryIdAndSha(String organizationId, String repositoryId, String sha);

    /** Active commits authored within {@code [from, to)}. */
    long countByOrganizationIdAndRepositoryIdAndAuthoredAtBetween(
            String organizationId, String repositoryId, Instant from, Instant to);

    /** Active commits of the repositories authored within {@code [from, to)}; empty without repositories. */
    List<VcsCommit> findAllByOrganizationIdAndRepositoryIdInAndAuthoredAtBetween(
            String organizationId, Collection<String> repositoryIds, Instant from, Instant to);

    /**
     * Same commits as {@link #findAllByOrganizationIdAndRepositoryIdInAndAuthoredAtBetween} with only
     * id, repository, message, author and authoring time — a lean read for counting or classifying
     * tens of thousands of commits. The default delegates; the Mongo adapter reads raw documents.
     */
    default List<VcsCommit> findMessagesByOrganizationIdAndRepositoryIdInAndAuthoredAtBetween(
            String organizationId, Collection<String> repositoryIds, Instant from, Instant to) {
        return findAllByOrganizationIdAndRepositoryIdInAndAuthoredAtBetween(organizationId, repositoryIds, from, to);
    }

    VcsCommit save(VcsCommit commit);
}
