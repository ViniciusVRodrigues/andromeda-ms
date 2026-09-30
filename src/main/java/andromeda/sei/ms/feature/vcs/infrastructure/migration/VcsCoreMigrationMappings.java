package andromeda.sei.ms.feature.vcs.infrastructure.migration;

import andromeda.sei.ms.feature.vcs.domain.ReviewKind;
import andromeda.sei.ms.feature.vcs.domain.VcsProvider;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * Pure {@code github_*} → {@code vcs_*} document mappings of {@link VcsCoreMigration}, kept apart so
 * the parity test can run the pre-refactor rows through exactly the conversion production uses.
 *
 * <p>Every migrated document keeps the {@code _id} of its source: pull requests, commits and review
 * events keep pointing at the same repository and pull request ids, and ids already known to the
 * frontend (repository and binding ids) stay valid.
 */
public final class VcsCoreMigrationMappings {

    static final String ID = "_id";
    static final String ORGANIZATION_ID = "organizationId";
    static final String PROVIDER = "provider";
    static final String ACTIVE = "active";
    static final String CREATED_AT = "createdAt";
    static final String UPDATED_AT = "updatedAt";
    static final String VERSION = "version";
    private static final String BOT_LOGIN_SUFFIX = "[bot]";
    private static final String GITHUB = VcsProvider.GITHUB.name();

    private VcsCoreMigrationMappings() {}

    /** {@code github_repos} row → {@code vcs_repositories}: numeric id as external id, full name as display name. */
    public static Document repository(Document githubRepo) {
        Document target = base(githubRepo);
        putIfPresent(target, "externalRepoId", githubRepo.get("externalId"));
        putIfPresent(target, "displayName", githubRepo.get("fullName"));
        putIfPresent(target, "defaultBranch", githubRepo.get("defaultBranch"));
        putIfPresent(target, "externalUpdatedAt", githubRepo.get("externalUpdatedAt"));
        return target;
    }

    /** A repository known only from a binding's {@code owner/name}: pending first sync (no external id). */
    public static Document pendingRepository(String organizationId, String fullName, Date now) {
        return new Document(ID, new ObjectId())
                .append(ORGANIZATION_ID, organizationId)
                .append(PROVIDER, GITHUB)
                .append("displayName", fullName)
                .append(ACTIVE, true)
                .append(CREATED_AT, now)
                .append(UPDATED_AT, now)
                .append(VERSION, 0L);
    }

    /**
     * {@code github_repo_bindings} row → {@code vcs_repo_bindings}, referencing the internal
     * repository id. A binding whose repository is pending first sync loses its watermark: the
     * next sync must read the backfill window, not only what changed since a run that never
     * stored anything.
     */
    public static Document binding(Document githubBinding, String repositoryId, boolean pending) {
        Document target = base(githubBinding)
                .append("projectId", githubBinding.get("projectId"))
                .append("repositoryId", repositoryId)
                .append("syncEnabled", Boolean.TRUE.equals(githubBinding.get("syncEnabled")));
        if (!pending) putIfPresent(target, "lastSyncAt", githubBinding.get("lastSyncAt"));
        return target;
    }

    /** {@code github_pull_requests} row → {@code vcs_pull_requests}; author login renamed, every metric kept. */
    public static Document pullRequest(Document githubPr) {
        Document target = base(githubPr).append("repositoryId", githubPr.get("repoId"));
        for (String field : List.of("externalId", "number", "title", "status", "authorUserId", "createdAt",
                "mergedAt", "closedAt", "externalUpdatedAt", "cycleTimeHours", "timeToFirstReviewHours",
                "timeToFirstApprovalHours", "linkedWorkItemExternalId", "draft", "baseBranch", "sourceBranch",
                "labels", "earliestCommitAt")) {
            putIfPresent(target, field, githubPr.get(field));
        }
        putIfPresent(target, "authorLogin", githubPr.get("authorGithubLogin"));
        return target;
    }

    /** {@code github_commits} row → {@code vcs_commits}. */
    public static Document commit(Document githubCommit) {
        Document target = base(githubCommit).append("repositoryId", githubCommit.get("repoId"));
        for (String field : List.of("sha", "message", "authorUserId", "authoredAt")) {
            putIfPresent(target, field, githubCommit.get(field));
        }
        putIfPresent(target, "authorLogin", githubCommit.get("authorGithubLogin"));
        return target;
    }

    /**
     * {@code github_reviews} row → {@code vcs_review_events}, or empty when the row never counted:
     * no submission time (a {@code PENDING} review) or no known state. {@code DISMISSED} becomes
     * {@code COMMENTED}: the original state is lost, and the review still counts as a review but not
     * as an approval — the pre-refactor rule. The bot flag folds in the {@code [bot]} login suffix
     * that the old policy applied to rows synced before the flag existed.
     */
    public static Optional<Document> reviewEvent(Document githubReview, String repositoryId) {
        ReviewKind kind = kindOf(githubReview.getString("result"));
        Object submittedAt = githubReview.get("submittedAt");
        if (kind == null || submittedAt == null) return Optional.empty();

        String login = githubReview.getString("reviewerGithubLogin");
        boolean bot = Boolean.TRUE.equals(githubReview.get("reviewerIsBot"))
                || (login != null && login.endsWith(BOT_LOGIN_SUFFIX));
        Document target = base(githubReview)
                .append("pullRequestId", githubReview.get("pullRequestId"))
                .append("externalEventId", githubReview.get("externalId"))
                .append("isBot", bot)
                .append("kind", kind.name())
                .append("occurredAt", submittedAt)
                .append("deleted", false);
        putIfPresent(target, "repositoryId", repositoryId);
        putIfPresent(target, "actorLogin", login);
        putIfPresent(target, "actorUserId", githubReview.get("reviewerUserId"));
        return Optional.of(target);
    }

    /** GitHub review state → normalized kind; {@code null} for states that never counted. */
    public static ReviewKind kindOf(String githubState) {
        if (githubState == null) return null;
        return switch (githubState) {
            case "APPROVED" -> ReviewKind.APPROVED;
            case "CHANGES_REQUESTED" -> ReviewKind.CHANGES_REQUESTED;
            case "COMMENTED", "DISMISSED" -> ReviewKind.COMMENTED;
            default -> null;
        };
    }

    /** Hex string of an id stored either as ObjectId or as string. */
    public static String idOf(Document document) {
        Object id = document.get(ID);
        return id instanceof ObjectId objectId ? objectId.toHexString() : id == null ? null : id.toString();
    }

    private static Document base(Document source) {
        Document target = new Document(ID, source.get(ID))
                .append(ORGANIZATION_ID, source.get(ORGANIZATION_ID))
                .append(PROVIDER, GITHUB)
                .append(ACTIVE, source.get(ACTIVE) == null || Boolean.TRUE.equals(source.get(ACTIVE)))
                .append(VERSION, 0L);
        putIfPresent(target, CREATED_AT, source.get(CREATED_AT));
        putIfPresent(target, UPDATED_AT, source.get(UPDATED_AT));
        return target;
    }

    private static void putIfPresent(Document target, String field, Object value) {
        if (value != null) target.append(field, value);
    }
}
