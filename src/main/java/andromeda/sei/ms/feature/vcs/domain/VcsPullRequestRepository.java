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

    /**
     * Active pull requests of the repositories that are still open or were created or merged at or
     * after {@code since}: everything a dashboard window starting at {@code since} reads (created/
     * merged in the window, open now), without the whole history. The default filters
     * {@link #findAllByOrganizationIdAndRepositoryId} in memory; the Mongo adapter queries by index.
     */
    default List<VcsPullRequest> findActiveTouchedSince(String organizationId, Collection<String> repositoryIds,
                                                        Instant since) {
        if (repositoryIds == null || repositoryIds.isEmpty()) return List.of();
        return repositoryIds.stream()
                .flatMap(id -> findAllByOrganizationIdAndRepositoryId(organizationId, id).stream())
                .filter(VcsPullRequest::isActive)
                .filter(pr -> pr.getStatus() == PullRequestStatus.OPEN
                        || (pr.getCreatedAt() != null && !pr.getCreatedAt().isBefore(since))
                        || (pr.getMergedAt() != null && !pr.getMergedAt().isBefore(since)))
                .toList();
    }

    /**
     * Same pull requests as {@link #findActiveTouchedSince} with only the fields period metrics read:
     * id, repository, provider, status, title, author (login, external id, user), creation, merge and
     * close times, cycle time, time to first review, linked work item, draft, base and source
     * branches, earliest commit. Every other field is {@code null}. The default delegates; the Mongo
     * adapter reads raw documents.
     */
    default List<VcsPullRequest> findMetricFieldsTouchedSince(String organizationId, Collection<String> repositoryIds,
                                                              Instant since) {
        return findActiveTouchedSince(organizationId, repositoryIds, since);
    }

    /** Pull requests authored by an Andromeda user, created within {@code [from, to)}. */
    List<VcsPullRequest> findAllByOrganizationIdAndAuthorUserIdAndCreatedAtBetween(
            String organizationId, String authorUserId, Instant from, Instant to);

    /** One page, newest first, optionally narrowed by status and author login (null = any). */
    VcsPullRequestPage findPageByRepository(String organizationId, String repositoryId,
                                            PullRequestStatus status, String authorLogin, int page, int size);

    VcsPullRequest save(VcsPullRequest pullRequest);

    /**
     * Gives an owner to the ownerless pull requests of the provider whose {@code authorExternalId} is
     * one of {@code externalIds}. Never overwrites an existing owner; scoped by organization.
     */
    VcsAttribution attributeByAuthorExternalIds(String organizationId, VcsProvider provider,
                                                Collection<String> externalIds, String userId);

    void updateMetrics(String organizationId, String id, Double cycleTimeHours, Double timeToFirstReviewHours,
                       Double timeToFirstApprovalHours);
}
