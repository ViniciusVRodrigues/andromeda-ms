package andromeda.sei.ms.feature.workitem.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SprintRepository {

    Sprint save(Sprint sprint);

    List<Sprint> findAllByOrganizationId(String organizationId);

    List<Sprint> findAllByOrganizationIdAndProjectId(String organizationId, String projectId);

    Optional<Sprint> findByOrganizationIdAndExternalIdAndTeamId(
            String organizationId, String externalId, String teamId);

    /**
     * Active sprints of the project with this iteration path. An iteration shared by several teams
     * has one sprint per team ("twins", see {@link SharedIterations}), so this is a list — pick the
     * canonical twin with {@link SharedIterations#canonical}. (The former single-result lookups by
     * path / external id threw {@code IncorrectResultSizeDataAccessException} on shared iterations.)
     */
    List<Sprint> findAllByOrganizationIdAndProjectIdAndPath(String organizationId, String projectId, String path);

    List<Sprint> findCurrentSprintsByOrganizationId(String organizationId);

    List<Sprint> findByOrganizationIdAndDay(String organizationId, LocalDate day);

    Optional<Sprint> findClosestSprintByOrganizationIdAndDay(String organizationId, LocalDate day);

    /**
     * The project's current sprint, deterministic when several teams have one: the first in
     * {@link SharedIterations#CANONICAL_ORDER} (for a shared iteration, its canonical twin).
     */
    Optional<Sprint> findCurrentSprintByOrganizationIdAndProjectId(String orgId, String projectId);

    Optional<Sprint> findById(String id);

    /** Tenant-scoped, active-only lookup by id — what every metric read must use. */
    Optional<Sprint> findByIdAndOrganizationIdAndActiveTrue(String id, String organizationId);
}
