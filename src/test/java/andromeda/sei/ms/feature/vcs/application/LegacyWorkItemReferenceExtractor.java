package andromeda.sei.ms.feature.vcs.application;

import andromeda.sei.ms.feature.organization.domain.WorkItemLinkConfig;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The extractor as it was before agente-endpoint-v2 (prefix based), kept only as the oracle of the parity test. */
final class LegacyWorkItemReferenceExtractor {

    private LegacyWorkItemReferenceExtractor() {}

    static Optional<String> extract(WorkItemLinkConfig config, String branchName, String body, String title) {
        WorkItemLinkConfig effective = WorkItemLinkConfig.orDefaults(config);
        if (!effective.isEnabled()) return Optional.empty();

        List<Pattern> patterns = effective.getPrefixes().stream()
                .filter(p -> p != null && !p.isBlank())
                .map(LegacyWorkItemReferenceExtractor::patternFor)
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

    private static Pattern patternFor(String prefix) {
        return Pattern.compile(Pattern.quote(prefix.trim()) + "(\\d+)(?!\\d)", Pattern.CASE_INSENSITIVE);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
