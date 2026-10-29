package andromeda.sei.ms.feature.agent.application.attribution;

import java.time.Instant;

/**
 * One agent usage row to attribute. {@code activeTask} is nullable; every other field but
 * {@code organizationId} and {@code userId} may be null or blank.
 */
public record AgentTaskQuery(String organizationId, String userId, String taskRef, String branch,
                             String repository, Instant bucketStart, ActiveTaskHint activeTask) {}
