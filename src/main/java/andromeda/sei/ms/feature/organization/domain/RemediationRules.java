package andromeda.sei.ms.feature.organization.domain;

import java.util.List;
import java.util.Objects;

/**
 * Which merged pull requests count as remediation — the "failed change" side of the Change Fail
 * Rate proxy (delivery-metrics).
 *
 * @param branchPrefixes              a source branch starting with any of these is a remediation
 * @param bugLinkedCountsAsRemediation a pull request linked to a work item of type BUG counts
 * @param revertCountsAsRemediation    a pull request titled {@code "Revert ..."} counts
 */
public record RemediationRules(List<String> branchPrefixes,
                               boolean bugLinkedCountsAsRemediation,
                               boolean revertCountsAsRemediation) {

    public static final String DEFAULT_BRANCH_PREFIX = "hotfix/";

    public RemediationRules {
        branchPrefixes = List.copyOf(Objects.requireNonNull(branchPrefixes, "branchPrefixes"));
    }

    public static RemediationRules defaults() {
        return new RemediationRules(List.of(DEFAULT_BRANCH_PREFIX), true, true);
    }

    /** True when every prefix has visible content — a blank prefix would match every branch. */
    public static boolean arePrefixesValid(List<String> prefixes) {
        return prefixes != null && prefixes.stream().allMatch(p -> p != null && !p.isBlank());
    }
}
