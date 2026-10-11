package andromeda.sei.ms.feature.dashboard.application.v2;

import andromeda.sei.ms.feature.workitem.domain.SharedIterations;
import andromeda.sei.ms.feature.workitem.domain.Sprint;
import andromeda.sei.ms.feature.workitem.domain.SprintRepository;
import andromeda.sei.ms.feature.workitem.domain.exception.SprintNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Resolves the sprint a v2 dashboard reads and its neighbours, inside one project of the caller's
 * organization.
 *
 * <ul>
 *   <li>{@code sprintId} informed: it must be an active sprint of this organization <b>and</b> of
 *       this project, otherwise {@link SprintNotFoundException} (404) — a sprint of another project
 *       never leaks through a project the caller may read.</li>
 *   <li>{@code sprintId} absent: the current sprint of the project, else the latest past one.</li>
 *   <li>An iteration shared by several teams is read through its canonical twin
 *       ({@link SharedIterations}), the one the work items are linked to, and listed once.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class DashboardSprintResolver {

    /** Comparison history: the selected sprint plus up to 5 before it (the v2 preview's window). */
    static final int HISTORY_SIZE = 6;
    /** Sprints before the selected one whose completed items set the p85 aging reference. */
    static final int AGING_REFERENCE_SPRINTS = 6;

    private final SprintRepository sprintRepository;

    public SprintSelection resolve(String organizationId, String projectId, String sprintId) {
        List<Sprint> projectSprints = sprintRepository.findAllByOrganizationIdAndProjectId(organizationId, projectId)
                .stream().filter(Sprint::isActive).toList();
        Sprint selected = sprintId != null
                ? requireSprintOfProject(organizationId, projectId, sprintId, projectSprints)
                : defaultSprint(projectSprints);
        if (selected == null || selected.getStartDate() == null || selected.getEndDate() == null) {
            return SprintSelection.EMPTY;
        }
        return around(selected, timeline(projectSprints));
    }

    private Sprint requireSprintOfProject(String organizationId, String projectId, String sprintId,
                                          List<Sprint> projectSprints) {
        Sprint sprint = sprintRepository.findByIdAndOrganizationIdAndActiveTrue(sprintId, organizationId)
                .filter(s -> projectId.equals(s.getProjectId()))
                .orElseThrow(() -> new SprintNotFoundException(sprintId));
        return SharedIterations.canonicalTwin(sprint, projectSprints);
    }

    private static Sprint defaultSprint(List<Sprint> projectSprints) {
        List<Sprint> distinct = SharedIterations.distinct(projectSprints);
        return distinct.stream().filter(s -> s.getTimeFrame() == Sprint.TimeFrame.CURRENT)
                .max(Comparator.comparing(DashboardSprintResolver::startOf))
                .orElseGet(() -> distinct.stream().filter(s -> s.getTimeFrame() == Sprint.TimeFrame.PAST)
                        .max(Comparator.comparing(DashboardSprintResolver::startOf))
                        .orElse(null));
    }

    /** One sprint per iteration with dates, oldest first. */
    private static List<Sprint> timeline(List<Sprint> projectSprints) {
        return SharedIterations.distinct(projectSprints).stream()
                .filter(s -> s.getId() != null && s.getStartDate() != null && s.getEndDate() != null)
                .sorted(Comparator.comparing(Sprint::getStartDate))
                .toList();
    }

    private static SprintSelection around(Sprint selected, List<Sprint> timeline) {
        int index = indexOf(timeline, selected);
        if (index < 0) return new SprintSelection(selected, null, List.of(selected), List.of());
        Sprint previous = index > 0 ? timeline.get(index - 1) : null;
        List<Sprint> history = timeline.subList(Math.max(0, index - (HISTORY_SIZE - 1)), index + 1);
        List<Sprint> agingReference = timeline.subList(Math.max(0, index - AGING_REFERENCE_SPRINTS), index);
        return new SprintSelection(selected, previous, List.copyOf(history), List.copyOf(agingReference));
    }

    private static int indexOf(List<Sprint> timeline, Sprint sprint) {
        for (int i = 0; i < timeline.size(); i++) {
            if (Objects.equals(timeline.get(i).getId(), sprint.getId())) return i;
        }
        return -1;
    }

    private static Instant startOf(Sprint sprint) {
        return sprint.getStartDate() != null ? sprint.getStartDate() : Instant.EPOCH;
    }

    /**
     * @param selected       the sprint the dashboard shows; {@code null} when the project has none
     * @param previous       the sprint right before it, or {@code null}
     * @param history        up to {@link #HISTORY_SIZE} sprints ending with {@code selected}, oldest first
     * @param agingReference up to {@link #AGING_REFERENCE_SPRINTS} sprints right before {@code selected}
     */
    public record SprintSelection(Sprint selected, Sprint previous, List<Sprint> history, List<Sprint> agingReference) {

        static final SprintSelection EMPTY = new SprintSelection(null, null, List.of(), List.of());

        public boolean isEmpty() {
            return selected == null;
        }
    }
}
