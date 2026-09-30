package andromeda.sei.ms.feature.vcs.application;

import andromeda.sei.ms.feature.organization.domain.WorkItemLinkConfig;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pulls a work item reference out of pull request text.
 *
 * <p>Sources are searched in decreasing order of reliability — branch name, then body, then
 * title. A branch name is short and conventional; a body carries explicit markers such as
 * "Closes AB#123"; a title is free prose that routinely contains unrelated numbers. The first
 * source yielding a candidate wins, and the rest are not searched.
 *
 * <p>A candidate must carry one of the organization's configured prefixes. Bare numbers are
 * never accepted: Azure work item ids are small sequential integers, and pull request text is
 * full of numbers that are not work items — HTTP status codes, API versions, dates, references
 * to other pull requests. A real title from this project makes the point:
 *
 * <pre>
 *   "Fix Azure work item sync failing with 400 on every WIQL query"
 * </pre>
 *
 * <p>Extraction only proposes a candidate. Whether it names a real work item is decided by the
 * caller, against the organization's own work items.
 */
public final class WorkItemReferenceExtractor {

    private WorkItemReferenceExtractor() {}

    /**
     * @return the first work item id found across branch, body and title, or empty when the
     *         configuration disables linking or no source carries a prefixed reference.
     */
    public static Optional<String> extract(WorkItemLinkConfig config,
                                            String branchName, String body, String title) {
        WorkItemLinkConfig effective = WorkItemLinkConfig.orDefaults(config);
        if (!effective.isEnabled()) return Optional.empty();

        List<Pattern> patterns = effective.getPrefixes().stream()
                .filter(p -> p != null && !p.isBlank())
                .map(WorkItemReferenceExtractor::patternFor)
                .toList();
        if (patterns.isEmpty()) return Optional.empty();

        for (String source : List.of(nullToEmpty(branchName), nullToEmpty(body), nullToEmpty(title))) {
            if (source.isEmpty()) continue;
            for (Pattern pattern : patterns) {
                Matcher matcher = pattern.matcher(source);
                if (matcher.find()) return Optional.of(matcher.group(1));
            }
        }
        return Optional.empty();
    }

    /**
     * Builds a matcher for one prefix. The prefix is quoted so a configured value carrying
     * regex metacharacters — {@code AB#} is harmless, but a team could configure {@code [WI]}
     * — is matched literally rather than interpreted.
     *
     * <p>The digits must not run on into another number: {@code AB#12} SHALL NOT match inside
     * {@code AB#1234}.
     */
    private static Pattern patternFor(String prefix) {
        return Pattern.compile(Pattern.quote(prefix.trim()) + "(\\d+)(?!\\d)",
                Pattern.CASE_INSENSITIVE);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
