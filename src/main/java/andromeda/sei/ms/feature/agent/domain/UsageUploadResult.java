package andromeda.sei.ms.feature.agent.domain;

import java.util.List;

/**
 * Outcome of one agent upload batch (agente-endpoint-v2, design 4.1): the counters and one
 * {@link RowResult} per row, in the order of the request. It is also what a replay of the same
 * {@code batchId} returns.
 */
public record UsageUploadResult(String batchId, int accepted, int duplicate, int rejected,
                                List<RowResult> results) {

    public enum Status { ACCEPTED, DUPLICATE, REJECTED }

    /** What became of an accepted or duplicate row. */
    public enum Allocation { ALLOCATED, QUEUED, HISTORY, SUPERSEDED }

    /**
     * {@code allocation}, {@code workItemId} and {@code taskRef} are set for accepted and
     * duplicate rows (the last two only for rows written now); {@code reason}, {@code field}
     * and {@code retryable} for rejected ones.
     */
    public record RowResult(String key, Status status, Allocation allocation, String workItemId,
                            String taskRef, String reason, String field, Boolean retryable) {

        public static RowResult rejected(String key, String reason, String field, boolean retryable) {
            return new RowResult(key, Status.REJECTED, null, null, null, reason, field, retryable);
        }
    }
}
