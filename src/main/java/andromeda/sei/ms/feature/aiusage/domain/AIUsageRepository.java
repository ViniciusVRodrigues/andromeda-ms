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

    List<AIUsage> findUnallocatedByOrganizationIdAndUserIdAndDay(
            String organizationId, String userId, LocalDate day);

    List<AIUsage> findUnallocatedByOrganizationIdAndUserId(String organizationId, String userId);

    List<AIUsage> findByOrganizationIdAndUserIdAndMonth(String organizationId, String userId,
            java.time.YearMonth month);

    List<AIUsage> findAllByOrganizationId(String organizationId);

    List<AIUsage> findByIds(List<String> ids);
}
