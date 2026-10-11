package andromeda.sei.ms.feature.aiusage.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface AIUsageRepository {

    void upsert(AIUsage usage);

    void updateCost(String organizationId, String userId, String datasetId, LocalDate day,
                    String model, String workspaceId, String machineId, BigDecimal costUsd);

    List<AIUsage> findByOrganizationIdAndUserIdAndDayBetween(
            String organizationId, String userId, LocalDate from, LocalDate to);

    List<AIUsage> findByOrganizationIdAndDayBetween(
            String organizationId, LocalDate from, LocalDate to);

    List<AIUsage> findByOrganizationIdAndUserIdAndDayIn(
            String organizationId, String userId, List<LocalDate> days);

    AiUsageSummary findSummaryByUserIdAndFilters(
            String organizationId, String userId, LocalDate startDate, LocalDate endDate, String model);

    List<AiUsageSummaryByModel> findSummaryGroupedByModel(
            String organizationId, String userId, LocalDate startDate, LocalDate endDate, String model);

    List<DailyAiUsage> findDailyUsageByUserIdAndDateRange(
            String organizationId, String userId, LocalDate from, LocalDate to, String model);

    Map<String, BigDecimal> findTotalCostByUserIdsAndDateRange(
            String organizationId, List<String> userIds, LocalDate from, LocalDate to);

    Map<String, Long> findTotalTokensByUserIdsAndDateRange(
            String organizationId, List<String> userIds, LocalDate from, LocalDate to);

    List<AIUsage> findUnallocatedByOrganizationIdAndUserIdAndDay(
            String organizationId, String userId, LocalDate day);

    List<AIUsage> findUnallocatedByOrganizationIdAndUserId(String organizationId, String userId);

    List<AIUsage> findByOrganizationIdAndUserIdAndMonth(String organizationId, String userId,
            java.time.YearMonth month);

    List<AIUsage> findAllByOrganizationId(String organizationId);

    /** Tenant-scoped, active-only lookup by ids. */
    List<AIUsage> findByOrganizationIdAndIdIn(String organizationId, java.util.Collection<String> ids);

    /** Active usage records of the users with {@code day} within {@code [from, to]} (inclusive). */
    List<AIUsage> findByOrganizationIdAndUserIdInAndDayBetween(
            String organizationId, java.util.Collection<String> userIds, LocalDate from, LocalDate to);

    /**
     * Usage of these users in {@code [from, to]}, summed per (user, day) in the database — one row per
     * person-day instead of every record. Empty without users. Index {@code {organizationId, userId, day}}.
     */
    List<AiUsageUserDay> summarizeByUserAndDay(String organizationId, java.util.Collection<String> userIds,
                                               LocalDate from, LocalDate to);

    /**
     * Active usage records by id with only the fields a cost breakdown reads: id, userId, day, model,
     * tokens, interactions, cost and allocationId; every other field is {@code null}/zero. A lean read
     * for large id sets — use {@link #findByOrganizationIdAndIdIn} when the whole record is needed.
     */
    List<AIUsage> findCostFieldsByOrganizationIdAndIdIn(String organizationId, java.util.Collection<String> ids);

    /**
     * Active usage of these users with {@code day} within {@code [from, to]}, with the fields an
     * aggregate dashboard reads: id, userId, day, model, editor, tokens, interactions, cost,
     * allocationId and uploadedAt; every other field {@code null}. Empty without users. The default
     * delegates; the Mongo adapter reads raw documents (index {@code {organizationId, userId, day}}).
     */
    /**
     * Usage of these users in {@code [from, to]} summed per user in the database (cost and tokens);
     * users without usage are absent. Empty without users. The default sums
     * {@link #findDashboardFieldsByOrganizationIdAndUserIdInAndDayBetween} in memory.
     */
    default List<AiUsageUserTotals> summarizeTotalsByUser(String organizationId, java.util.Collection<String> userIds,
                                                         LocalDate from, LocalDate to) {
        if (userIds == null || userIds.isEmpty()) return List.of();
        java.util.Map<String, List<AIUsage>> byUser = findDashboardFieldsByOrganizationIdAndUserIdInAndDayBetween(
                organizationId, userIds, from, to).stream().filter(u -> u.getUserId() != null)
                .collect(java.util.stream.Collectors.groupingBy(AIUsage::getUserId));
        return byUser.entrySet().stream().map(e -> new AiUsageUserTotals(e.getKey(),
                e.getValue().stream().map(AIUsage::getCostUsd).filter(java.util.Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add),
                e.getValue().stream().mapToLong(AIUsage::getInputTokens).sum(),
                e.getValue().stream().mapToLong(AIUsage::getOutputTokens).sum())).toList();
    }

    default List<AIUsage> findDashboardFieldsByOrganizationIdAndUserIdInAndDayBetween(
            String organizationId, java.util.Collection<String> userIds, LocalDate from, LocalDate to) {
        return findByOrganizationIdAndUserIdInAndDayBetween(organizationId, userIds, from, to);
    }
}
