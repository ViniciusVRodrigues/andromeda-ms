package andromeda.sei.ms.feature.github.application.dto;

import andromeda.sei.ms.feature.github.domain.PullRequestStatus;

import java.time.Instant;

/** One row of the repository pull request listing. */
public record RepoPullRequestOutput(
        String id,
        int number,
        String title,
        PullRequestStatus status,
        String authorGithubLogin,
        String authorUserId,
        Instant createdAt,
        Instant mergedAt,
        Instant closedAt,
        Double cycleTimeHours,
        Double timeToFirstReviewHours,
        /** Azure work item externalId, present only when the link resolved to a real item. */
        String linkedWorkItemExternalId,
        String linkedWorkItemId,
        String linkedWorkItemTitle) {}
