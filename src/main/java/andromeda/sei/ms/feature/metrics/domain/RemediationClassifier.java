package andromeda.sei.ms.feature.metrics.domain;

import andromeda.sei.ms.feature.organization.domain.RemediationRules;

/**
 * Decides whether a merged change was a remediation — the "failure" of the Change Fail Rate proxy
 * (delivery-metrics, design D2). Any enabled rule is enough: linked to a BUG work item, a source
 * branch with a configured prefix, or a title starting with {@code "Revert "}.
 */
public final class RemediationClassifier {

    static final String REVERT_TITLE_PREFIX = "Revert ";

    private RemediationClassifier() {}

    public static boolean isRemediation(MergedChange change, RemediationRules rules) {
        if (rules.bugLinkedCountsAsRemediation() && change.linkedToBug()) return true;
        if (rules.revertCountsAsRemediation() && isRevertTitle(change.title())) return true;
        return hasRemediationPrefix(change.sourceBranch(), rules);
    }

    private static boolean isRevertTitle(String title) {
        return title != null
                && title.regionMatches(true, 0, REVERT_TITLE_PREFIX, 0, REVERT_TITLE_PREFIX.length());
    }

    private static boolean hasRemediationPrefix(String sourceBranch, RemediationRules rules) {
        if (sourceBranch == null) return false;
        return rules.branchPrefixes().stream().anyMatch(sourceBranch::startsWith);
    }
}
