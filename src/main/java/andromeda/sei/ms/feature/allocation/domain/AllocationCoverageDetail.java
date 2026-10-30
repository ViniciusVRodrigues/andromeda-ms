package andromeda.sei.ms.feature.allocation.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * The usage cited by one validated allocation, summed per (day, model) of the usage RECORD (not of
 * the allocation). Only found records produce a row. The splits carry only work item and percentage.
 */
public record AllocationCoverageDetail(String allocationId, String userId, List<AllocationSplit> splits,
                                       LocalDate day, String model, long inputTokens, long outputTokens,
                                       long interactions, BigDecimal costUsd) {}
