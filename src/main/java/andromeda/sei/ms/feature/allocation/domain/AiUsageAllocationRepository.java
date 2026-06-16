package andromeda.sei.ms.feature.allocation.domain;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AiUsageAllocationRepository {

    List<AiUsageAllocation> findByOrganizationIdAndUserIdAndDay(
            String organizationId, String userId, LocalDate day);

    List<AiUsageAllocation> findPendingByOrganizationIdAndUserId(
            String organizationId, String userId);

    boolean existsValidatedByOrganizationIdAndUserIdAndDay(
            String organizationId, String userId, LocalDate day);

    void saveAll(List<AiUsageAllocation> allocations);

    AiUsageAllocation save(AiUsageAllocation allocation);

    void deleteByOrganizationIdAndUserIdAndDay(
            String organizationId, String userId, LocalDate day);

    void deleteById(String id);

    List<AiUsageAllocation> findByOrgUserAndStatus(
            String organizationId, String userId, AiUsageAllocation.Status status);

    List<AiUsageAllocation> findByOrgUserAndDay(
            String organizationId, String userId, LocalDate day);

    void updateAllocationIdOnUsageRecords(List<String> aiUsageIds, String allocationId);

    List<AiUsageAllocation> findByWorkItemId(String workItemId);

    Optional<AiUsageAllocation> findById(String id, String organizationId);

    Page<AiUsageAllocation> findValidatedByOrgUserAndOptionalMonth(
            String organizationId, String userId, YearMonth month, Pageable pageable);

    List<AiUsageAllocation> findValidatedByOrgUserAndWorkItemId(
            String organizationId, String userId, String workItemId);
}