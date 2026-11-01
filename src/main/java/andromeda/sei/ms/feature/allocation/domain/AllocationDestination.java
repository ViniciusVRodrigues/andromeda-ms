package andromeda.sei.ms.feature.allocation.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * One {@code VALIDATED} or {@code AWAITING_APPROVAL} allocation with the cost and tokens (input + output)
 * of the usage records it cites, summed in the database, for the "where did the AI cost go" blocks of the
 * dashboards (uso-ia-fora-de-entrega). The splits carry kind, work item, percentage and category.
 */
public record AllocationDestination(String id, String userId, LocalDate day, AiUsageAllocation.Status status,
                                    List<AllocationSplit> splits, BigDecimal costUsd, long tokens) {}
