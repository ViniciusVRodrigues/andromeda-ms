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

    Optional<AiUsageAllocation> findById(String id, String organizationId);

    Page<AiUsageAllocation> findValidatedByOrgUserAndOptionalMonth(
            String organizationId, String userId, YearMonth month, Pageable pageable);

    List<AiUsageAllocation> findValidatedByOrgUserAndWorkItemId(
            String organizationId, String userId, String workItemId);

    /** Active {@code VALIDATED} allocations of the organization whose day is within {@code [from, to]}. */
    List<AiUsageAllocation> findValidatedByOrganizationIdAndDayBetween(
            String organizationId, LocalDate from, LocalDate to);

    /**
     * Active {@code VALIDATED} allocations of the organization, day within {@code [from, to]}, that
     * split to any of the work items; empty without ids. Index {@code {organizationId,
     * splits.workItemId, day}}.
     */
    List<AiUsageAllocation> findValidatedByOrganizationIdAndWorkItemIdInAndDayBetween(
            String organizationId, java.util.Collection<String> workItemIds, LocalDate from, LocalDate to);

    /**
     * Active {@code VALIDATED} allocations of these users, day within {@code [from, to]}; empty
     * without users. Index {@code {organizationId, userId, day}}.
     */
    List<AiUsageAllocation> findValidatedByOrganizationIdAndUserIdInAndDayBetween(
            String organizationId, java.util.Collection<String> userIds, LocalDate from, LocalDate to);

    /** Active {@code VALIDATED} allocations of the organization splitting to any of the work items. */
    List<AiUsageAllocation> findValidatedByOrganizationIdAndWorkItemIdIn(
            String organizationId, java.util.Collection<String> workItemIds);

    /**
     * Distinct (user, day) pairs of these users with an active {@code VALIDATED} allocation in
     * {@code [from, to]}, grouped in the database. Empty without users.
     */
    List<ValidatedUserDay> findValidatedUserDays(String organizationId, java.util.Collection<String> userIds,
                                                 LocalDate from, LocalDate to);
}
