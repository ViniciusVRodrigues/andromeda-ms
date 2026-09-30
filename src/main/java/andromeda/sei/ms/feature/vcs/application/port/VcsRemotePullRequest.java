package andromeda.sei.ms.feature.vcs.application.port;

import andromeda.sei.ms.feature.vcs.domain.PullRequestStatus;

import java.time.Instant;
import java.util.List;

/**
 * A pull request as the provider reports it (neutral input DTO); {@code status} is already
 * derived by the adapter from the provider's own state.
 *
 * @param authorEmail provider identity email (Azure DevOps {@code uniqueName}), a fallback for
 *                    author resolution; {@code null} when the provider does not expose it
 */
public record VcsRemotePullRequest(
        String externalId,
        Integer number,
        String title,
        String body,
        PullRequestStatus status,
        String authorLogin,
        String authorExternalId,
        Instant createdAt,
        Instant updatedAt,
        Instant mergedAt,
        Instant closedAt,
        String sourceBranch,
        String baseBranch,
        boolean draft,
        List<String> labels,
        String authorEmail) {

    public VcsRemotePullRequest(String externalId, Integer number, String title, String body,
                                PullRequestStatus status, String authorLogin, String authorExternalId,
                                Instant createdAt, Instant updatedAt, Instant mergedAt, Instant closedAt,
                                String sourceBranch, String baseBranch, boolean draft, List<String> labels) {
        this(externalId, number, title, body, status, authorLogin, authorExternalId, createdAt, updatedAt,
                mergedAt, closedAt, sourceBranch, baseBranch, draft, labels, null);
    }
}
