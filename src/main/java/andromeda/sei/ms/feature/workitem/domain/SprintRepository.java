package andromeda.sei.ms.feature.workitem.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SprintRepository {

    Sprint save(Sprint sprint);

    List<Sprint> findAllByOrganizationIdAndProjectId(String organizationId, String projectId);

    Optional<Sprint> findByOrganizationIdAndExternalIdAndTeamId(
            String organizationId, String externalId, String teamId);

    Optional<Sprint> findByOrganizationIdAndExternalId(String organizationId, String externalId);

    Optional<Sprint> findByOrganizationIdAndPath(String organizationId, String path);

    List<Sprint> findCurrentSprintsByOrganizationId(String organizationId);

    List<Sprint> findByOrganizationIdAndDay(String organizationId, LocalDate day);

    Optional<Sprint> findClosestSprintByOrganizationIdAndDay(String organizationId, LocalDate day);

    Optional<Sprint> findCurrentSprintByOrganizationIdAndProjectId(String orgId, String projectId);

    Optional<Sprint> findById(String id);
}
