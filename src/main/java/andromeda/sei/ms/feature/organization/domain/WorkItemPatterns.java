package andromeda.sei.ms.feature.organization.domain;

import andromeda.sei.ms.feature.agent.domain.attribution.BranchPatternMatcher;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The organization's single branch to work item pattern set (agente-endpoint-v2, design 14.2),
 * used by the local agent and by the pull request work item link.
 *
 * @param enabled     false disables the link (pull requests) and the branch step (agent)
 * @param mode        DEFAULT never configured, DETECTED applied by the detection, CUSTOM saved or confirmed
 * @param patterns    never empty; group 1 of the first pattern that matches is the reference
 * @param detection   last detection run (package 6); null until then
 */
public record WorkItemPatterns(boolean enabled, Mode mode, List<Entry> patterns, String confirmedBy,
                               Instant confirmedAt, String updatedBy, Instant updatedAt, Detection detection) {

    public enum Mode { DEFAULT, DETECTED, CUSTOM }

    /** {@code preset} is computed by the server from the regex (AB_HASH, PREFIX, JIRA_KEY, NUMBER, CUSTOM). */
    public record Entry(String regex, String preset, boolean alsoInText) {

        /** Computes the preset from the regex. */
        public static Entry of(String regex, boolean alsoInText) {
            return new Entry(regex, BranchPatternMatcher.classify(regex), alsoInText);
        }

        /** Default for {@code alsoInText} by preset: true for AB_HASH and PREFIX (as before), false otherwise. */
        public static boolean defaultAlsoInText(String regex) {
            String preset = BranchPatternMatcher.classify(regex);
            return "AB_HASH".equals(preset) || "PREFIX".equals(preset);
        }
    }

    public record Detection(Instant ranAt, int branchesScanned, double coverage, boolean applied,
                            List<Candidate> candidates) {
        public Detection {
            candidates = candidates == null ? List.of() : List.copyOf(candidates);
        }
    }

    public record Candidate(String regex, String preset, double coverage, double precision, int matchedBranches,
                            int resolvedBranches, List<Example> examples) {
        public Candidate {
            examples = examples == null ? List.of() : List.copyOf(examples);
        }
    }

    public record Example(String branch, String workItemNumber, String title) {}

    public WorkItemPatterns {
        mode = mode == null ? Mode.DEFAULT : mode;
        patterns = patterns == null || patterns.isEmpty() ? defaultEntries() : List.copyOf(patterns);
    }

    public static List<Entry> defaultEntries() {
        return BranchPatternMatcher.DEFAULTS.stream().map(r -> Entry.of(r, Entry.defaultAlsoInText(r))).toList();
    }

    public static WorkItemPatterns defaults() {
        return new WorkItemPatterns(true, Mode.DEFAULT, defaultEntries(), null, null, null, null, null);
    }

    /** True when it is the never-configured state (mode DEFAULT, default patterns, no audit data). */
    public boolean isUntouchedDefault() {
        return enabled && mode == Mode.DEFAULT && patterns.equals(defaultEntries()) && detection == null
                && confirmedBy == null && updatedBy == null;
    }

    public List<String> regexes() {
        return patterns.stream().map(Entry::regex).toList();
    }

    /** Entries that also run over the pull request title and description. */
    public List<Entry> textEntries() {
        return patterns.stream().filter(Entry::alsoInText).toList();
    }

    /**
     * Literal prefixes of the AB_HASH and PREFIX patterns (legacy route view); empty when disabled,
     * as the former {@code WorkItemLinkConfig#getPrefixes}.
     */
    public List<String> legacyPrefixes() {
        if (!enabled) return List.of();
        List<String> out = new ArrayList<>();
        for (Entry e : patterns) {
            if ("AB_HASH".equals(e.preset())) out.add(WorkItemLinkConfig.DEFAULT_PREFIX);
            else if ("PREFIX".equals(e.preset())) BranchPatternMatcher.literalPrefix(e.regex()).ifPresent(out::add);
        }
        return out;
    }

    /** From the former {@code vcsSettings.workItemLink} (design 14.3): each prefix becomes a literal pattern. */
    public static WorkItemPatterns fromLegacy(WorkItemLinkConfig config) {
        if (config == null) return defaults();
        List<Entry> entries = new ArrayList<>();
        for (String raw : config.rawPrefixes()) {
            if (raw == null || raw.isBlank()) continue;
            entries.add(fromPrefix(raw.trim()));
        }
        // enabled with nothing configured behaved as the default prefix
        if (entries.isEmpty()) entries.addAll(defaultEntries());
        boolean sameAsDefault = config.isEnabled() && entries.equals(defaultEntries());
        return new WorkItemPatterns(config.isEnabled(), sameAsDefault ? Mode.DEFAULT : Mode.CUSTOM,
                entries, null, null, null, null, null);
    }

    /** A literal, case-insensitive prefix pattern followed by digits; AB# is classified AB_HASH. */
    public static Entry fromPrefix(String prefix) {
        return Entry.of(BranchPatternMatcher.prefixRegex(prefix), true);
    }

    public Optional<Detection> detectionOpt() {
        return Optional.ofNullable(detection);
    }
}
