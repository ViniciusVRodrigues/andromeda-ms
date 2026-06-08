package andromeda.sei.ms.feature.allocation.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AiUsageAllocationRepository {

    List<AiUsageAllocation> findByOrganizationIdAndUserIdAndDay(
            String organizationId, String userId, LocalDate day);

    List<AiUsageAllocation> findPendingByOrganizationIdAndUserId(
            String organizationId, String userId);

    boolean existsValidatedByOrganizationIdAndUserIdAndDay(
            String organizationId, String userId, LocalDate day);

    void saveAll(List<AiUsageAllocation> allocations);

    void deleteByOrganizationIdAndUserIdAndDay(
            String organizationId, String userId, LocalDate day);
}
