package andromeda.sei.ms.feature.vcs;

import andromeda.sei.ms.feature.vcs.domain.PullRequestStatus;
import andromeda.sei.ms.feature.vcs.domain.VcsProvider;
import andromeda.sei.ms.feature.vcs.domain.VcsPullRequest;
import andromeda.sei.ms.feature.vcs.domain.VcsRepoBinding;
import andromeda.sei.ms.feature.vcs.domain.VcsRepository;
import andromeda.sei.ms.feature.vcs.domain.VcsReviewEvent;
import andromeda.sei.ms.feature.vcs.infrastructure.migration.VcsCoreMigrationMappings;

import java.time.Instant;

/**
 * Builds neutral entities with the constructor shapes of the pre-refactor GitHub types
 * ({@code GithubPullRequest}, {@code GithubRepo}, {@code GithubReview}). The tests written against
 * those types keep their fixtures and expected values verbatim and now run them through the neutral
 * code — the parity evidence of vcs-core-refactor task 8.5.
 *
 * <p>A legacy review state is normalized by {@link VcsCoreMigrationMappings#kindOf}, the mapping the
 * data migration applies, so fixtures and migrated rows cannot diverge.
 */
public final class VcsLegacyFixtures {

    /** The pre-refactor {@code ReviewResult}. */
    public enum LegacyReviewResult {
        APPROVED,
        CHANGES_REQUESTED,
        COMMENTED,
        DISMISSED
    }

    private VcsLegacyFixtures() {}

    /** Shape of {@code new GithubPullRequest(...)}. */
    public static VcsPullRequest pullRequest(String id, String organizationId, String repoId, String externalId,
                                             Integer number, String title, PullRequestStatus status,
                                             String authorLogin, String authorUserId, Instant createdAt,
                                             Instant mergedAt, Instant closedAt, Instant externalUpdatedAt,
                                             Double cycleTimeHours, Double timeToFirstReviewHours,
                                             boolean active, Instant updatedAt, Long version) {
        VcsPullRequest pr = new VcsPullRequest(id, organizationId, repoId, VcsProvider.GITHUB, externalId, number,
                title, status);
        pr.setAuthorLogin(authorLogin);
        pr.setAuthorUserId(authorUserId);
        pr.setCreatedAt(createdAt);
        pr.setMergedAt(mergedAt);
        pr.setClosedAt(closedAt);
        pr.setExternalUpdatedAt(externalUpdatedAt);
        pr.setCycleTimeHours(cycleTimeHours);
        pr.setTimeToFirstReviewHours(timeToFirstReviewHours);
        pr.setActive(active);
        pr.setUpdatedAt(updatedAt);
        pr.setVersion(version);
        return pr;
    }

    /** Shape of {@code new GithubRepo(...)}: the full name becomes the display name. */
    public static VcsRepository repository(String id, String organizationId, String externalId, String owner,
                                           String name, String fullName, Instant externalUpdatedAt,
                                           boolean active, Instant createdAt, Instant updatedAt, Long version) {
        VcsRepository repo = new VcsRepository(id, organizationId, VcsProvider.GITHUB, externalId, fullName);
        repo.setExternalUpdatedAt(externalUpdatedAt);
        repo.setActive(active);
        repo.setCreatedAt(createdAt);
        repo.setUpdatedAt(updatedAt);
        repo.setVersion(version);
        return repo;
    }

    /** Shape of {@code new GithubReview(...)}; the state goes through the migration's normalization. */
    public static VcsReviewEvent reviewEvent(String id, String organizationId, String pullRequestId, String externalId,
                                             String reviewerLogin, String reviewerUserId, LegacyReviewResult result,
                                             Instant submittedAt, boolean active, Instant createdAt,
                                             Instant updatedAt, Long version) {
        VcsReviewEvent event = new VcsReviewEvent(id, organizationId, pullRequestId, null, VcsProvider.GITHUB,
                externalId, result == null ? null : VcsCoreMigrationMappings.kindOf(result.name()), submittedAt);
        event.setActorLogin(reviewerLogin);
        event.setActorUserId(reviewerUserId);
        event.setActive(active);
        event.setCreatedAt(createdAt);
        event.setUpdatedAt(updatedAt);
        event.setVersion(version);
        return event;
    }

    /** Shape of {@code new GithubRepoBinding(...)}, with the internal repository id instead of {@code owner/name}. */
    public static VcsRepoBinding binding(String id, String organizationId, String projectId, String repositoryId,
                                         boolean syncEnabled, Instant lastSyncAt, boolean active,
                                         Instant createdAt, Instant updatedAt, Long version) {
        VcsRepoBinding binding = new VcsRepoBinding(id, organizationId, projectId, repositoryId, VcsProvider.GITHUB,
                syncEnabled);
        binding.setLastSyncAt(lastSyncAt);
        binding.setActive(active);
        binding.setCreatedAt(createdAt);
        binding.setUpdatedAt(updatedAt);
        binding.setVersion(version);
        return binding;
    }
}
