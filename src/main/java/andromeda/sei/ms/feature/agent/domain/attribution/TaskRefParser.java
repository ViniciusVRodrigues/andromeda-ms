package andromeda.sei.ms.feature.agent.domain.attribution;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Normalizes a task reference typed or detected by the agent into a work item {@code externalId}:
 * {@code AB#123}, {@code ab#123}, {@code #123}, {@code 123} give {@code "123"}; {@code PROJ-45}
 * (Jira key) gives {@code "PROJ-45"}. Anything else is not a reference.
 */
public final class TaskRefParser {

    private static final Pattern NUMERIC = Pattern.compile("^(?:[aA][bB])?#?(\\d{1,12})$");
    private static final Pattern JIRA = Pattern.compile("^([A-Za-z][A-Za-z0-9]{1,9}-\\d{1,9})$");

    private TaskRefParser() {}

    public static Optional<String> parse(String raw) {
        if (raw == null) return Optional.empty();
        String ref = raw.trim();
        if (ref.isEmpty() || ref.length() > 40) return Optional.empty();
        Matcher numeric = NUMERIC.matcher(ref);
        if (numeric.matches()) return Optional.of(numeric.group(1).replaceFirst("^0+(?=\\d)", ""));
        Matcher jira = JIRA.matcher(ref);
        if (jira.matches()) return Optional.of(jira.group(1).toUpperCase(Locale.ROOT));
        return Optional.empty();
    }
}
