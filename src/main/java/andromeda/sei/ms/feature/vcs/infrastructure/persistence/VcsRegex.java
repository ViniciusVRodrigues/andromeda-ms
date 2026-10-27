package andromeda.sei.ms.feature.vcs.infrastructure.persistence;

import java.util.regex.Pattern;

/** Literal, anchored, case-insensitive match: the value never acts as a regular expression. */
final class VcsRegex {

    private VcsRegex() {}

    static Pattern exactIgnoreCase(String value) {
        return Pattern.compile("^" + Pattern.quote(value.trim()) + "$", Pattern.CASE_INSENSITIVE);
    }
}
