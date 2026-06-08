package andromeda.sei.ms.feature.aiusage.domain;

import java.time.LocalDate;
import java.util.List;

public interface AIUsageRepository {

    void upsert(AIUsage usage);

    List<AIUsage> findByOrganizationIdAndUserIdAndDayBetween(
            String organizationId, String userId, LocalDate from, LocalDate to);

    List<AIUsage> findByOrganizationIdAndDayBetween(
            String organizationId, LocalDate from, LocalDate to);
}
