package andromeda.sei.ms.feature.metrics.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * A merged pull request seen through the delivery metrics, independent of the VCS provider.
 *
 * @param id               stable id, used to de-duplicate a repository bound to several projects
 * @param baseBranch       branch the change was merged into; {@code null} when unknown
 * @param defaultBranch    the repository's default branch; {@code null} when unknown
 * @param linkedToBug      the linked work item resolves to type BUG
 * @param earliestCommitAt authoring time of the earliest commit; {@code null} when not fetched
 */
public record MergedChange(
        String id,
        String title,
        String sourceBranch,
        String baseBranch,
        String defaultBranch,
        boolean linkedToBug,
        Instant createdAt,
        Instant earliestCommitAt,
        Instant mergedAt) {

    public MergedChange {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(mergedAt, "mergedAt");
    }

    /**
     * Merged into the default branch. Unknown branches (rows synced before the branch fields
     * existed, until a repair re-reads them) count as default-branch merges: {@code null} means
     * "not known", not "another branch", and excluding them would blank every metric until then.
     */
    public boolean isIntoDefaultBranch() {
        return baseBranch == null || defaultBranch == null || baseBranch.equals(defaultBranch);
    }

    /** Change lead time start: first commit when known, pull request creation otherwise. */
    public Instant leadTimeStart() {
        return earliestCommitAt != null ? earliestCommitAt : createdAt;
    }
}
