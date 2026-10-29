package andromeda.sei.ms.feature.vcs.application;

import andromeda.sei.ms.feature.agent.domain.attribution.BranchPatternMatcher;
import andromeda.sei.ms.feature.organization.domain.WorkItemPatterns;

import java.util.List;
import java.util.Optional;

/**
 * Pulls a work item reference out of pull request text with the organization's work item patterns
 * (agente-endpoint-v2, design 14).
 *
 * <p>Sources are searched in decreasing order of reliability: branch name, then description, then
 * title. The branch runs every pattern; description and title run only the patterns with
 * {@code alsoInText}, because a bare number in free prose matches almost anything. The first
 * source yielding group 1 wins, and the rest are not searched. Whether the reference names a
 * real work item is decided by the caller; the pull request link does not check it.
 */
public final class WorkItemReferenceExtractor {

    private WorkItemReferenceExtractor() {}

    /**
     * @return group 1 of the first matching pattern across branch, body and title, or empty when
     *         the patterns are disabled or no source carries a reference.
     */
    public static Optional<String> extract(WorkItemPatterns patterns, String branchName, String body, String title) {
        WorkItemPatterns effective = patterns == null ? WorkItemPatterns.defaults() : patterns;
        if (!effective.enabled()) return Optional.empty();

        BranchPatternMatcher all = BranchPatternMatcher.of(effective.regexes());
        List<String> textRegexes = effective.textEntries().stream().map(WorkItemPatterns.Entry::regex).toList();
        BranchPatternMatcher text = textRegexes.isEmpty() ? null : BranchPatternMatcher.of(textRegexes);

        // The branch is matched in full (no 255 cut): the former extractor had none, and parity matters.
        Optional<String> fromBranch = all.matchText(branchName);
        if (fromBranch.isPresent()) return fromBranch;
        if (text == null) return Optional.empty();
        Optional<String> fromBody = text.matchText(body);
        if (fromBody.isPresent()) return fromBody;
        return text.matchText(title);
    }
}
