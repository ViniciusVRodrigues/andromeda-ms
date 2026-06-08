package andromeda.sei.ms.feature.workitem.domain;

import java.util.List;
import java.util.Optional;

public interface SprintMetricsRepository {

    SprintMetrics save(SprintMetrics sprintMetrics);

    Optional<SprintMetrics> findByOrganizationIdAndSprintIdAndSource(
            String organizationId, String sprintId, WorkItem.Source source);

    List<SprintMetrics> findAllByOrganizationIdAndProjectId(String organizationId, String projectId);
}
