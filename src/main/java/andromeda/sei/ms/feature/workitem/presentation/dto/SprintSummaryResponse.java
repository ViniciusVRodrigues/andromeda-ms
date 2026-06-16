package andromeda.sei.ms.feature.workitem.presentation.dto;

import andromeda.sei.ms.feature.workitem.domain.Sprint;

import java.time.ZoneOffset;

public record SprintSummaryResponse(
        String id,
        String name,
        String externalId,
        String timeFrame,
        String startDate,
        String finishDate
) {
    public static SprintSummaryResponse from(Sprint sprint) {
        return new SprintSummaryResponse(
                sprint.getId(),
                sprint.getName(),
                sprint.getExternalId(),
                sprint.getTimeFrame() != null ? sprint.getTimeFrame().name() : null,
                sprint.getStartDate() != null
                        ? sprint.getStartDate().atZone(ZoneOffset.UTC).toLocalDate().toString() : null,
                sprint.getEndDate() != null
                        ? sprint.getEndDate().atZone(ZoneOffset.UTC).toLocalDate().toString() : null
        );
    }
}
