package andromeda.sei.ms.feature.aiusage.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * AI usage of one person on one day, summed in the database: total cost, the cost of the records
 * no allocation claimed yet ({@code allocationId} absent), how many records, and whether any record
 * is claimed by an allocation.
 */
public record AiUsageUserDay(String userId, LocalDate day, BigDecimal costUsd, BigDecimal unallocatedCostUsd,
                             long records, boolean anyAllocated) {}
