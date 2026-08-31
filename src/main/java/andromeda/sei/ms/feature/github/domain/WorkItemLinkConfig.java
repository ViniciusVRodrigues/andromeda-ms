package andromeda.sei.ms.feature.github.domain;

import java.util.List;

/**
 * How this organization marks a work item reference inside pull request text.
 *
 * <p>Naming conventions differ across organizations, so the prefix cannot be a constant: a
 * fixed value would work for some and fail silently for others. An organization may configure
 * several prefixes (teams migrating between conventions run both for a while) or disable
 * extraction outright when its wording produces noise rather than signal.
 *
 * <p>Modelled as an embedded value object rather than a collection of its own, mirroring how
 * {@code WorkflowConfig} is embedded in {@code AzureProjectBinding}. It currently hangs off the
 * organization's connection, but nothing here is org-specific — narrowing the scope later (one
 * config per repo binding, say) means embedding the same type somewhere else, with no change to
 * the extraction logic that reads it.
 */
public class WorkItemLinkConfig {

    /**
     * The Azure Boards convention for referencing a work item from GitHub. Applied when an
     * organization has configured nothing, so teams already following it get links for free.
     */
    public static final String DEFAULT_PREFIX = "AB#";

    private final boolean enabled;
    private final List<String> prefixes;

    public WorkItemLinkConfig(boolean enabled, List<String> prefixes) {
        this.enabled = enabled;
        this.prefixes = prefixes == null ? List.of() : List.copyOf(prefixes);
    }

    /** The configuration applied to an organization that has not set one up. */
    public static WorkItemLinkConfig defaults() {
        return new WorkItemLinkConfig(true, List.of(DEFAULT_PREFIX));
    }

    /** Falls back to {@link #defaults()} for an organization with no stored configuration. */
    public static WorkItemLinkConfig orDefaults(WorkItemLinkConfig config) {
        return config == null ? defaults() : config;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * @return the configured prefixes, or the default one when extraction is on but none were
     *         set — an enabled configuration with no prefix would silently never match.
     */
    public List<String> getPrefixes() {
        if (!enabled) return List.of();
        return prefixes.isEmpty() ? List.of(DEFAULT_PREFIX) : prefixes;
    }
}
