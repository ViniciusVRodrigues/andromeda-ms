package andromeda.sei.ms.feature.allocation.application;

import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation.ReviewCode;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation.ReviewReason;
import andromeda.sei.ms.feature.workitem.domain.Sprint;
import andromeda.sei.ms.feature.workitem.domain.SprintRepository;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.domain.WorkItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;

/**
 * The reasons to review an agent suggestion of one item on one day (atribuicao-a-prova-de-esquecimento,
 * 1.2). One read of the item and of the project's sprints per call; the spike is a day-level reason.
 */
@Component
@RequiredArgsConstructor
public class AgentReviewReasonCalculator {

    private final WorkItemRepository workItemRepository;
    private final SprintRepository sprintRepository;
    private final UsageSpikeDetector spikeDetector;

    public List<ReviewReason> reasonsFor(String organizationId, String userId, LocalDate day, String workItemId) {
        List<ReviewReason> reasons = new ArrayList<>();
        WorkItem item = workItemId == null ? null
                : workItemRepository.findByIdAndOrganizationIdAndActiveTrue(workItemId, organizationId).orElse(null);
        if (item != null) {
            String displayId = displayId(item);
            if (isClosedBefore(item, day)) {
                reasons.add(new ReviewReason(ReviewCode.ITEM_CLOSED, item.getId(), displayId, null));
            }
            String assignee = item.getAssigneeUserId();
            if (assignee != null && !assignee.isBlank() && !assignee.equals(userId)) {
                reasons.add(new ReviewReason(ReviewCode.OTHER_ASSIGNEE, item.getId(), displayId, null));
            }
            if (isOutOfSprint(organizationId, item, day)) {
                reasons.add(new ReviewReason(ReviewCode.OUT_OF_SPRINT, item.getId(), displayId, null));
            }
        }
        OptionalLong ratio = spikeDetector.ratioOf(organizationId, userId, day);
        if (ratio.isPresent()) {
            reasons.add(new ReviewReason(ReviewCode.USAGE_SPIKE, null, null, ratio.getAsLong()));
        }
        return reasons;
    }

    public static String displayId(WorkItem item) {
        return item.getSource() == WorkItem.Source.JIRA ? item.getExternalId() : "AB#" + item.getExternalId();
    }

    /** DONE with a completion day before the day, or DONE with no completion date at all. */
    private static boolean isClosedBefore(WorkItem item, LocalDate day) {
        if (item.getResolvedStatus() != WorkItem.ResolvedStatus.DONE) return false;
        if (item.getCompletedAt() == null) return true;
        return item.getCompletedAt().atZone(ZoneOffset.UTC).toLocalDate().isBefore(day);
    }

    /** The project has dated sprints and none that holds the day is the item's sprint (no sprint counts too). */
    private boolean isOutOfSprint(String organizationId, WorkItem item, LocalDate day) {
        if (item.getProjectId() == null) return false;
        List<Sprint> dated = sprintRepository.findAllByOrganizationIdAndProjectId(organizationId, item.getProjectId())
                .stream().filter(s -> s.isActive() && s.getStartDate() != null && s.getEndDate() != null).toList();
        if (dated.isEmpty()) return false;
        return dated.stream().noneMatch(s -> holds(s, day) && s.getId() != null && s.getId().equals(item.getSprintId()));
    }

    private static boolean holds(Sprint sprint, LocalDate day) {
        LocalDate start = sprint.getStartDate().atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate end = sprint.getEndDate().atZone(ZoneOffset.UTC).toLocalDate();
        return !day.isBefore(start) && !day.isAfter(end);
    }
}
