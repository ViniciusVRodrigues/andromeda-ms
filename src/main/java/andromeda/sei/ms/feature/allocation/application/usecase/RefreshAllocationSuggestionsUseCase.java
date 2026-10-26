package andromeda.sei.ms.feature.allocation.application.usecase;

import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocationRepository;
import andromeda.sei.ms.feature.azure.application.dto.ActivityDay;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * Recomputes the allocation suggestion of each (user, day) that got new Azure activity in a sync.
 * A {@code VALIDATED} day never changes; a failure on one day is logged and never propagates, so a
 * refresh can not break the sync that triggered it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshAllocationSuggestionsUseCase {

    private final AutoAssignAllocationUseCase autoAssign;
    private final AiUsageAllocationRepository allocationRepository;

    public void execute(String organizationId, Set<ActivityDay> days) {
        if (days == null || days.isEmpty()) return;
        for (ActivityDay activityDay : days) {
            try {
                if (allocationRepository.existsValidatedByOrganizationIdAndUserIdAndDay(
                        organizationId, activityDay.userId(), activityDay.day())) {
                    continue;
                }
                autoAssign.execute(organizationId, activityDay.userId(), activityDay.day(), true);
            } catch (Exception e) {
                log.warn("Allocation refresh failed for org={} user={} day={}", organizationId,
                        activityDay.userId(), activityDay.day(), e);
            }
        }
    }
}
