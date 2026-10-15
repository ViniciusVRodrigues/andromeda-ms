package andromeda.sei.ms.feature.usage.domain;

import java.util.regex.Pattern;

/**
 * Second barrier against personal data in a click label (the client sanitizes first): e-mail ->
 * [email], digit -> #, no control characters, whitespace collapsed, cut at 60.
 */
public final class UsageLabelSanitizer {

    static final int MAX_LENGTH = 60;

    private static final Pattern EMAIL = Pattern.compile("[^\\s@]+@[^\\s@]+\\.[^\\s@]+");
    private static final Pattern WHITESPACE_CONTROL = Pattern.compile("[\\t\\n\\r\\u000B\\f\\u0085\\u2028\\u2029]");
    private static final Pattern OTHER_CONTROL = Pattern.compile("[\\p{Cntrl}\\p{Cf}]");
    private static final Pattern DIGIT = Pattern.compile("\\d");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    private UsageLabelSanitizer() {
    }

    /** @return the clean label, or {@code null} when nothing is left. */
    public static String sanitize(String raw) {
        if (raw == null) return null;
        String text = WHITESPACE_CONTROL.matcher(raw).replaceAll(" ");
        text = OTHER_CONTROL.matcher(text).replaceAll("");
        text = EMAIL.matcher(text).replaceAll("[email]");
        text = DIGIT.matcher(text).replaceAll("#");
        text = SPACES.matcher(text).replaceAll(" ").trim();
        if (text.length() > MAX_LENGTH) text = text.substring(0, MAX_LENGTH).trim();
        return text.isEmpty() ? null : text;
    }
}
