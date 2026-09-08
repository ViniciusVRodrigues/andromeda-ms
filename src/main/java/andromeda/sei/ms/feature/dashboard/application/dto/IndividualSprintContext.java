package andromeda.sei.ms.feature.dashboard.application.dto;

import java.time.LocalDate;
import java.util.List;

public record IndividualSprintContext(String sprintId, String sprintName, LocalDate startDate, LocalDate endDate,
        Double avgTimeToFirstReviewHours, Double avgPrCycleTimeHours, int reviewedPrs,
        List<PullRequest> pullRequests) {
    public record PullRequest(String id, Integer number, String title, String status,
            Double timeToFirstReviewHours, Double cycleTimeHours, String linkedWorkItemExternalId) {}
}
