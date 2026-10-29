package andromeda.sei.ms.feature.agent.domain.attribution;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Extracts a task reference from a branch name using the organization's patterns (group 1 is the
 * reference). Patterns are tried in order; the first that matches wins. Matching is protected
 * against catastrophic regexes: the branch is cut at 255 characters and the whole match has a
 * 50 ms deadline (a timeout counts as "no match", never as an exception).
 */
public final class BranchPatternMatcher {

    public static final int MAX_PATTERNS = 10;
    public static final int MAX_PATTERN_LENGTH = 200;
    public static final int MAX_BRANCH_LENGTH = 255;
    public static final Duration DEADLINE = Duration.ofMillis(50);

    /**
     * Defaults (design 5.3). The Jira key comes before the numeric pattern: {@code PROJ-45-login}
     * would otherwise match the numeric one first and resolve to {@code 45}.
     */
    public static final List<String> DEFAULTS = List.of(
            "AB#(\\d+)",
            "([A-Z][A-Z0-9]{1,9}-\\d+)",
            "(?i)(?:^|/)(?:[a-z]+[-_])?(\\d{2,7})(?=[-_/.]|$)");

    public enum ErrorCode { PATTERN_INVALID, PATTERN_NO_GROUP, PATTERN_TOO_LONG, PATTERN_TOO_MANY }

    /** One rejected entry; {@code index} is the entry (for PATTERN_TOO_MANY, the first excess one). */
    public record Error(int index, ErrorCode code, String reason) {}

    /** A successful match: group 1, the pattern that produced it and the span of the whole match in the branch. */
    public record Hit(String group, int patternIndex, int start, int end) {}

    private record Indexed(int index, Pattern pattern) {}

    private final List<Indexed> patterns;

    private BranchPatternMatcher(List<Indexed> patterns) {
        this.patterns = patterns;
    }

    /**
     * Matcher over {@code patterns}, or over the defaults when null or empty. Invalid entries are
     * skipped (their indexes are kept for the others).
     */
    public static BranchPatternMatcher of(List<String> patterns) {
        List<String> source = patterns == null || patterns.isEmpty() ? DEFAULTS : patterns;
        List<Indexed> compiled = new ArrayList<>();
        for (int i = 0; i < source.size(); i++) {
            String p = source.get(i);
            try {
                if (p != null) compiled.add(new Indexed(i, Pattern.compile(p)));
            } catch (PatternSyntaxException ignored) {
                // stored lists are validated on save; a bad leftover must not break attribution
            }
        }
        return new BranchPatternMatcher(List.copyOf(compiled));
    }

    public static BranchPatternMatcher defaults() {
        return of(null);
    }

    /** Every problem of the list, in index order; empty when valid (null or empty = defaults, valid). */
    public static List<Error> validateAll(List<String> patterns) {
        List<Error> errors = new ArrayList<>();
        if (patterns == null) return errors;
        for (int i = 0; i < patterns.size(); i++) {
            if (i == MAX_PATTERNS) {
                errors.add(new Error(MAX_PATTERNS, ErrorCode.PATTERN_TOO_MANY, "at most " + MAX_PATTERNS + " patterns"));
                break;
            }
            String p = patterns.get(i);
            if (p == null || p.isBlank()) {
                errors.add(new Error(i, ErrorCode.PATTERN_INVALID, "pattern is blank"));
            } else if (p.length() > MAX_PATTERN_LENGTH) {
                errors.add(new Error(i, ErrorCode.PATTERN_TOO_LONG, "pattern longer than " + MAX_PATTERN_LENGTH + " characters"));
            } else {
                try {
                    if (Pattern.compile(p).matcher("").groupCount() < 1) {
                        errors.add(new Error(i, ErrorCode.PATTERN_NO_GROUP, "pattern needs at least one capturing group"));
                    }
                } catch (PatternSyntaxException e) {
                    errors.add(new Error(i, ErrorCode.PATTERN_INVALID, "pattern does not compile: " + e.getDescription()));
                }
            }
        }
        return errors;
    }

    /** Group 1 of the first pattern that matches, or empty (no match, null branch or timeout). */
    public Optional<String> match(String branch) {
        return matchDetailed(branch).map(Hit::group);
    }

    /** Like {@link #match} with the pattern index and the span of the match in the (cut) branch. */
    public Optional<Hit> matchDetailed(String branch) {
        if (branch == null || branch.isBlank()) return Optional.empty();
        String cut = branch.length() > MAX_BRANCH_LENGTH ? branch.substring(0, MAX_BRANCH_LENGTH) : branch;
        long deadline = System.nanoTime() + DEADLINE.toNanos();
        DeadlineCharSequence input = new DeadlineCharSequence(cut, deadline);
        try {
            for (Indexed entry : patterns) {
                Matcher m = entry.pattern().matcher(input);
                if (m.find()) {
                    String group = m.groupCount() >= 1 ? m.group(1) : null;
                    if (group != null && !group.isBlank()) {
                        return Optional.of(new Hit(group, entry.index(), m.start(), m.end()));
                    }
                }
            }
        } catch (MatchTimeoutException timeout) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    private static final class MatchTimeoutException extends RuntimeException {
        MatchTimeoutException() {
            super("branch pattern match timed out", null, false, false);
        }
    }

    private record DeadlineCharSequence(CharSequence delegate, long deadlineNanos) implements CharSequence {
        @Override
        public char charAt(int index) {
            if (System.nanoTime() > deadlineNanos) throw new MatchTimeoutException();
            return delegate.charAt(index);
        }

        @Override
        public int length() {
            return delegate.length();
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            return new DeadlineCharSequence(delegate.subSequence(start, end), deadlineNanos);
        }

        @Override
        public String toString() {
            return delegate.toString();
        }
    }
}
