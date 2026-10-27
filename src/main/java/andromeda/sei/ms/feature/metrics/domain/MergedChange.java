package andromeda.sei.ms.feature.metrics.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * A merged pull request seen through the delivery metrics, independent of the VCS provider.
 *
 * @param id               stable id, used to de-duplicate a repository bound to several projects
 * @param baseBranch       branch the change was merged into; {@code null} when unknown
 * @param productionBranch the binding's production branch (the repository's default branch when
 *                         there is no binding or none was chosen); {@code null} when unknown
 * @param linkedToBug      the linked work item resolves to type BUG
 * @param earliestCommitAt authoring time of the earliest commit; {@code null} when not fetched
 */
public record MergedChange(
        String id,
        String title,
        String sourceBranch,
        String baseBranch,
        String productionBranch,
        boolean linkedToBug,
        Instant createdAt,
        Instant earliestCommitAt,
        Instant mergedAt) {

    public MergedChange {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(mergedAt, "mergedAt");
    }

    /**
     * Merged into the production branch. Unknown branches (rows synced before the branch fields
     * existed, until a repair re-reads them) count as production merges: {@code null} means
     * "not known", not "another branch", and excluding them would blank every metric until then.
     */
    public boolean isIntoProductionBranch() {
        return baseBranch == null || productionBranch == null || baseBranch.equals(productionBranch);
    }

    /** Change lead time start: first commit when known, pull request creation otherwise. */
    public Instant leadTimeStart() {
        return earliestCommitAt != null ? earliestCommitAt : createdAt;
    }
}
