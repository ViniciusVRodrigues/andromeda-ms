package andromeda.sei.ms.feature.allocation.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * One validated allocation with the cost and tokens (input + output) of the usage records it cites,
 * summed in the database. An allocation whose records are not found still appears, with zeros. The
 * splits carry only work item and percentage.
 */
public record AllocationCoverage(String id, String userId, LocalDate day, List<AllocationSplit> splits,
                                 BigDecimal costUsd, long tokens) {}
