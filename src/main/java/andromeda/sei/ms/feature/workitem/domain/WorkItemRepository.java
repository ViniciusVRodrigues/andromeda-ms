package andromeda.sei.ms.feature.workitem.domain;

import java.util.List;
import java.util.Optional;

public interface WorkItemRepository {

    WorkItem save(WorkItem workItem);

    Optional<WorkItem> findByOrganizationIdAndExternalIdAndSource(
            String organizationId, String externalId, WorkItem.Source source);

    List<WorkItem> findAllByOrganizationIdAndProjectId(String organizationId, String projectId);

    void updateMetrics(String id, Double cycleTimeHours, Double leadTimeHours);
}
