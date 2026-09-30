package andromeda.sei.ms.feature.vcs.application.dto;

import andromeda.sei.ms.feature.vcs.domain.PullRequestStatus;
import andromeda.sei.ms.feature.vcs.domain.VcsProvider;

import java.time.Instant;

/**
 * One row of the repository pull request listing. {@code linkedWorkItem*} are present only when
 * the stored work item reference resolves to a real item at read time.
 */
public record RepositoryPullRequestOutput(
        String id,
        int number,
        String title,
        PullRequestStatus status,
        String authorLogin,
        String authorUserId,
        Instant createdAt,
        Instant mergedAt,
        Instant closedAt,
        Double cycleTimeHours,
        Double timeToFirstReviewHours,
        String linkedWorkItemExternalId,
        String linkedWorkItemId,
        String linkedWorkItemTitle,
        boolean draft,
        Double timeToFirstApprovalHours,
        VcsProvider provider) {}
