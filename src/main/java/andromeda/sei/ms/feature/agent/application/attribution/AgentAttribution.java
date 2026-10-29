package andromeda.sei.ms.feature.agent.application.attribution;

/**
 * Outcome for one row: {@code resolvedTask} is null when no work item was resolved
 * ({@code source == NONE}); {@code repositoryProjectId} is the project bound to the row repository,
 * set even without a task (null when unknown or ambiguous).
 */
public record AgentAttribution(ResolvedTask resolvedTask, String repositoryProjectId, AttributionSource source) {}
