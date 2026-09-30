package andromeda.sei.ms.feature.vcs.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Port of {@code vcs_pull_requests}. Every query is scoped by {@code organizationId}; the list
 * queries return active rows only unless stated otherwise. Windows are half-open {@code [from, to)}.
 */
public interface VcsPullRequestRepository {

    Optional<VcsPullRequest> findByOrganizationIdAndRepositoryIdAndExternalId(
            String organizationId, String repositoryId, String externalId);

    Optional<VcsPullRequest> findByIdAndOrganizationId(String id, String organizationId);

    /** Every pull request of a repository, inactive rows included (the caller filters). */
    List<VcsPullRequest> findAllByOrganizationIdAndRepositoryId(String organizationId, String repositoryId);

    List<VcsPullRequest> findAllByOrganizationIdAndRepositoryIdInAndStatus(
            String organizationId, Collection<String> repositoryIds, PullRequestStatus status);

    List<VcsPullRequest> findMergedByOrganizationIdAndRepositoryIdInAndMergedAtBetween(
            String organizationId, Collection<String> repositoryIds, Instant from, Instant to);

    List<VcsPullRequest> findAllByOrganizationIdAndRepositoryIdInAndCreatedAtBetween(
            String organizationId, Collection<String> repositoryIds, Instant from, Instant to);

    /** Pull requests authored by an Andromeda user, created within {@code [from, to)}. */
    List<VcsPullRequest> findAllByOrganizationIdAndAuthorUserIdAndCreatedAtBetween(
            String organizationId, String authorUserId, Instant from, Instant to);

    /** One page, newest first, optionally narrowed by status and author login (null = any). */
    VcsPullRequestPage findPageByRepository(String organizationId, String repositoryId,
                                            PullRequestStatus status, String authorLogin, int page, int size);

    VcsPullRequest save(VcsPullRequest pullRequest);

    void updateMetrics(String organizationId, String id, Double cycleTimeHours, Double timeToFirstReviewHours,
                       Double timeToFirstApprovalHours);
}
