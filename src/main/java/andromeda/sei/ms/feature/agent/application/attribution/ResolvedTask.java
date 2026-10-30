package andromeda.sei.ms.feature.agent.application.attribution;

/**
 * The work item a usage row was attributed to. {@code ref} is {@code AB#<externalId>} for Azure
 * DevOps and the external id for Jira. {@code source} says which step resolved it. {@code overriddenTaskRef}
 * is the ref of the item the branch beat (the row taskRef or the active task), null otherwise.
 */
public record ResolvedTask(String workItemId, String projectId, String title, String ref, AttributionSource source,
                           String overriddenTaskRef) {

    public ResolvedTask(String workItemId, String projectId, String title, String ref, AttributionSource source) {
        this(workItemId, projectId, title, ref, source, null);
    }

    public ResolvedTask withOverridden(String overridden) {
        return new ResolvedTask(workItemId, projectId, title, ref, source, overridden);
    }
}
