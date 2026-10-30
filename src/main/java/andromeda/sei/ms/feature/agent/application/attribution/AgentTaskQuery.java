package andromeda.sei.ms.feature.agent.application.attribution;

import java.time.Instant;
import java.time.LocalDate;

/**
 * One agent usage row to attribute. {@code activeTask} is nullable; every other field but
 * {@code organizationId} and {@code userId} may be null or blank. {@code day} is the local day of the row.
 */
public record AgentTaskQuery(String organizationId, String userId, String taskRef, String branch,
                             String repository, Instant bucketStart, ActiveTaskHint activeTask, LocalDate day) {

    public AgentTaskQuery(String organizationId, String userId, String taskRef, String branch,
                          String repository, Instant bucketStart, ActiveTaskHint activeTask) {
        this(organizationId, userId, taskRef, branch, repository, bucketStart, activeTask, null);
    }
}
