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

    /**
     * Active commits of the repositories authored within {@code [from, to)}, counted per UTC day.
     * The default counts {@link #findMessagesByOrganizationIdAndRepositoryIdInAndAuthoredAtBetween};
     * the Mongo adapter groups in the database (index {@code {organizationId, repositoryId, authoredAt}}).
     */
    default java.util.Map<java.time.LocalDate, Long> countByDay(String organizationId, Collection<String> repositoryIds,
                                                               Instant from, Instant to) {
        if (repositoryIds == null || repositoryIds.isEmpty()) return java.util.Map.of();
        return findMessagesByOrganizationIdAndRepositoryIdInAndAuthoredAtBetween(organizationId, repositoryIds, from, to)
                .stream().filter(c -> c.getAuthoredAt() != null)
                .collect(java.util.stream.Collectors.groupingBy(
                        c -> c.getAuthoredAt().atZone(java.time.ZoneOffset.UTC).toLocalDate(),
                        java.util.stream.Collectors.counting()));
    }

    VcsCommit save(VcsCommit commit);
}
