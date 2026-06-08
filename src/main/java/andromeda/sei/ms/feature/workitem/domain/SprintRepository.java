package andromeda.sei.ms.feature.workitem.domain;

import java.util.List;
import java.util.Optional;

public interface SprintRepository {

    Sprint save(Sprint sprint);

    List<Sprint> findAllByOrganizationIdAndProjectId(String organizationId, String projectId);

    Optional<Sprint> findByOrganizationIdAndExternalIdAndTeamId(
            String organizationId, String externalId, String teamId);

    Optional<Sprint> findByOrganizationIdAndExternalId(String organizationId, String externalId);
}
