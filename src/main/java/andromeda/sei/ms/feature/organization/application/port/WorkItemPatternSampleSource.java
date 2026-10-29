package andromeda.sei.ms.feature.organization.application.port;

import java.time.Instant;
import java.util.List;

/**
 * Port of the branch sample of the pattern detection (agente-endpoint-v2, design 14.5): distinct
 * source branches of the organization's active pull requests plus the branches of the local agent
 * rows, since {@code since}. Implemented in the infrastructure with projection queries.
 */
public interface WorkItemPatternSampleSource {

    /**
     * Distinct branch names seen since {@code since}, most recently seen first, at most {@code limit}.
     * Always scoped by {@code organizationId}; inactive pull requests and rows are left out.
     */
    List<String> recentBranches(String organizationId, Instant since, int limit);
}
