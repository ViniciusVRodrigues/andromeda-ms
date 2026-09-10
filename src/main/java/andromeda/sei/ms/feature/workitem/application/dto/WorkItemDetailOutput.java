package andromeda.sei.ms.feature.workitem.application.dto;

import java.time.Instant;
import java.util.List;

public record WorkItemDetailOutput(
        String id, String externalId, String title, String type, String status,
        String projectId, String sprintName, String assignee, String priority,
        List<String> labels, String parentExternalId,
        Instant createdAt, Instant startedAt, Instant completedAt,
        Double cycleTimeHours, Double leadTimeHours, Double tokensPerSp,
        boolean editableType, FieldDetail complexity, FieldDetail effort) {
    public record FieldDetail(Double value, String source, String mappedField, String fieldType) {}
}
