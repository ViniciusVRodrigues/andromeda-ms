package andromeda.sei.ms.feature.allocation.application.usecase;

import andromeda.sei.ms.feature.azure.application.dto.ActivityDay;
import andromeda.sei.ms.feature.workitem.domain.WorkItemActivityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * After a user is linked to an Azure identity, the activity stored earlier with no owner (the
 * person was not linked yet) becomes theirs, and the allocation suggestions of the affected days
 * are recomputed (a {@code VALIDATED} day never changes). A failure is logged and never propagates,
 * so it can not break the link that triggered it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReattributeAzureActivityUseCase {

    private final WorkItemActivityRepository activityRepository;
    private final RefreshAllocationSuggestionsUseCase refreshSuggestions;

    public void execute(String organizationId, String userId, String azureId, String email) {
        try {
            List<LocalDate> days = activityRepository.assignOwnerToUnowned(organizationId, azureId, email, userId);
            if (days == null || days.isEmpty()) return;
            Set<ActivityDay> activityDays = days.stream()
                    .map(day -> new ActivityDay(userId, day))
                    .collect(Collectors.toSet());
            refreshSuggestions.execute(organizationId, activityDays);
        } catch (RuntimeException e) {
            log.warn("Activity reattribution failed for org={} userId={}", organizationId, userId, e);
        }
    }
}
