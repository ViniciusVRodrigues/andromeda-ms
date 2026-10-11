package andromeda.sei.ms.feature.dashboard.application.v2;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Type of a commit from the conventional-commit prefix of its first line ({@code type(scope)!: …}).
 * The v2 preview's six types are recognized; anything else — another prefix, a merge commit, free
 * text — is {@value #OTHER}.
 */
public final class CommitKinds {

    public static final String FEAT = "feat";
    public static final String FIX = "fix";
    public static final String REFACTOR = "refactor";
    public static final String TEST = "test";
    public static final String CHORE = "chore";
    public static final String DOCS = "docs";
    public static final String OTHER = "other";
    /** Display order of the series; {@link #OTHER} last. */
    public static final List<String> ALL = List.of(FEAT, FIX, REFACTOR, TEST, CHORE, DOCS, OTHER);

    private static final Pattern PREFIX = Pattern.compile(
            "^\\s*(feat|fix|refactor|test|chore|docs)(\\([^)]*\\))?!?:", Pattern.CASE_INSENSITIVE);

    private CommitKinds() {}

    public static String kindOf(String message) {
        if (message == null) return OTHER;
        String firstLine = message.lines().findFirst().orElse("");
        Matcher matcher = PREFIX.matcher(firstLine);
        return matcher.find() ? matcher.group(1).toLowerCase(Locale.ROOT) : OTHER;
    }
}
