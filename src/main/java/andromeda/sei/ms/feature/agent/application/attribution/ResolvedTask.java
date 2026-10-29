package andromeda.sei.ms.feature.agent.application.attribution;

/**
 * The work item a usage row was attributed to. {@code ref} is {@code AB#<externalId>} for Azure
 * DevOps and the external id for Jira. {@code source} says which step resolved it.
 */
public record ResolvedTask(String workItemId, String projectId, String title, String ref, AttributionSource source) {}
