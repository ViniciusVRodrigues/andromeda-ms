package andromeda.sei.ms.feature.allocation.application.dto;

import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** One day waiting for the caller's approval. */
public record ApprovalQueueItemOutput(String allocationId, String userId, String userName, LocalDate day,
                                      BigDecimal costUsd, long tokens, List<SplitOutput> splits,
                                      List<AiUsageAllocation.ApprovalReason> reasons, Instant requestedAt,
                                      List<Repository> repositories) {

    public record Repository(String name, String branch) {}
}
