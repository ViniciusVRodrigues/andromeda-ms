package andromeda.sei.ms.feature.agent.domain.attribution;

import java.util.Optional;

/**
 * Converts a Java regex of the work item patterns to JavaScript (design 5.3), for the agent to show
 * what the server will conclude. Conservative: whatever has no safe JS equivalent answers empty and
 * the agent says "the server decides". Pure.
 *
 * <p>Converted: a leading {@code (?i)} becomes the flag {@code i}; {@code \Q...\E} becomes an
 * escaped literal. Refused: other inline flags, atomic groups, possessive quantifiers,
 * {@code \A \Z \z \G \h \R \X \p \P}, nested classes and class intersections.
 */
public final class PortableRegex {

    public record Portable(String source, String flags) {}

    private static final String JS_SPECIAL = "\\^$.*+?()[]{}|/";
    private static final String REFUSED_ESCAPES = "AZzGhRXpPHVN";

    private PortableRegex() {}

    public static Optional<Portable> convert(String javaRegex) {
        if (javaRegex == null || javaRegex.isEmpty()) return Optional.empty();
        String flags = "";
        String rest = javaRegex;
        if (rest.startsWith("(?i)")) {
            flags = "i";
            rest = rest.substring(4);
        }
        StringBuilder out = new StringBuilder();
        boolean inClass = false;
        boolean quantifierBefore = false;
        int i = 0;
        while (i < rest.length()) {
            char c = rest.charAt(i);
            if (c == '\\') {
                if (i + 1 >= rest.length()) return Optional.empty();
                char n = rest.charAt(i + 1);
                if (n == 'Q') {
                    int end = rest.indexOf("\\E", i + 2);
                    String literal = end < 0 ? rest.substring(i + 2) : rest.substring(i + 2, end);
                    for (char l : literal.toCharArray()) {
                        if (JS_SPECIAL.indexOf(l) >= 0) out.append('\\');
                        out.append(l);
                    }
                    i = end < 0 ? rest.length() : end + 2;
                    quantifierBefore = false;
                    continue;
                }
                if (n == 'E' || REFUSED_ESCAPES.indexOf(n) >= 0) return Optional.empty();
                out.append(c).append(n);
                i += 2;
                quantifierBefore = false;
                continue;
            }
            if (inClass) {
                if (c == '[') return Optional.empty();
                if (c == '&' && i + 1 < rest.length() && rest.charAt(i + 1) == '&') return Optional.empty();
                if (c == ']') inClass = false;
                out.append(c);
                i++;
                continue;
            }
            switch (c) {
                case '[' -> {
                    inClass = true;
                    quantifierBefore = false;
                    out.append(c);
                }
                case '(' -> {
                    if (i + 1 < rest.length() && rest.charAt(i + 1) == '?') {
                        if (!allowedGroupStart(rest, i)) return Optional.empty();
                    }
                    quantifierBefore = false;
                    out.append(c);
                }
                case '*', '+', '?', '}' -> {
                    if (c == '+' && quantifierBefore) return Optional.empty(); // possessive
                    if (c == '?' && quantifierBefore) {
                        quantifierBefore = false; // lazy
                    } else {
                        quantifierBefore = true;
                    }
                    out.append(c);
                }
                default -> {
                    quantifierBefore = false;
                    out.append(c);
                }
            }
            i++;
        }
        if (inClass) return Optional.empty();
        return Optional.of(new Portable(out.toString(), flags));
    }

    /** {@code (?:}, {@code (?=}, {@code (?!}, {@code (?<=}, {@code (?<!} and {@code (?<name>} exist in JavaScript. */
    private static boolean allowedGroupStart(String s, int at) {
        String tail = s.substring(at);
        if (tail.startsWith("(?:") || tail.startsWith("(?=") || tail.startsWith("(?!")
                || tail.startsWith("(?<=") || tail.startsWith("(?<!")) {
            return true;
        }
        return tail.startsWith("(?<") && tail.length() > 3 && Character.isLetter(tail.charAt(3));
    }
}
