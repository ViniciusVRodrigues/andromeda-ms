package andromeda.sei.ms.feature.agent.application.usage;

import java.util.List;
import java.util.Set;

/**
 * An upload batch as the controller read it. Values of the wrong JSON type are not carried: the
 * name of the field goes in {@link Row#malformed()} and the row is rejected with INVALID_FIELD, so a
 * single bad row never fails the whole batch.
 */
public record UsageBatchInput(String batchId, String agentVersion, String timezone, List<Row> rows) {

    /** Token counters are {@code null} when absent. */
    public record Row(String key, String tool, String sessionHash, String model, String hour, String day,
                      Long input, Long output, Long cacheRead, Long cacheWrite, Long thinking,
                      Long interactions, String origin, String repo, String branch, String taskRef,
                      String attributionMethod, Boolean branchApproximate, Set<String> malformed) {

        public Row(String key, String tool, String sessionHash, String model, String hour, String day,
                   Long input, Long output, Long cacheRead, Long cacheWrite, Long thinking,
                   Long interactions, String origin, String repo, String branch, String taskRef,
                   String attributionMethod, Set<String> malformed) {
            this(key, tool, sessionHash, model, hour, day, input, output, cacheRead, cacheWrite, thinking,
                    interactions, origin, repo, branch, taskRef, attributionMethod, null, malformed);
        }

        public Row {
            malformed = malformed == null ? Set.of() : malformed;
        }
    }
}
