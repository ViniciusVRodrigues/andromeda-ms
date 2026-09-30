package andromeda.sei.ms.feature.workitem.application.usecase;

import andromeda.sei.ms.feature.project.domain.ProjectRepository;
import andromeda.sei.ms.feature.workitem.domain.SharedIterations;
import andromeda.sei.ms.feature.workitem.domain.Sprint;
import andromeda.sei.ms.feature.workitem.domain.SprintRepository;
import andromeda.sei.ms.shared.domain.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GetProjectSprintsUseCase {

    private final SprintRepository sprintRepository;
    private final ProjectRepository projectRepository;

    public List<Sprint> execute(String orgId, String projectId) {
        projectRepository.findById(projectId)
                .filter(p -> orgId.equals(p.getOrganizationId()))
                .orElseThrow(() -> new ResourceNotFoundException("PROJECT_NOT_FOUND", "Project not found: " + projectId));

        // One entry per iteration: a shared iteration is one sprint per team, listed through its
        // canonical twin (the one the work items are linked to).
        List<Sprint> sprints = SharedIterations.distinct(sprintRepository.findAllByOrganizationIdAndProjectId(orgId, projectId));

        return sprints.stream()
                .sorted(Comparator
                        .comparingInt((Sprint s) -> sprintOrder(s.getTimeFrame()))
                        .thenComparing((Sprint s) -> {
                            if (s.getTimeFrame() == Sprint.TimeFrame.PAST) {
                                return s.getEndDate() != null ? s.getEndDate().toEpochMilli() * -1 : 0L;
                            } else if (s.getTimeFrame() == Sprint.TimeFrame.FUTURE) {
                                return s.getStartDate() != null ? s.getStartDate().toEpochMilli() : Long.MAX_VALUE;
                            }
                            return 0L;
                        }))
                .toList();
    }

    private int sprintOrder(Sprint.TimeFrame timeFrame) {
        return switch (timeFrame) {
            case CURRENT -> 0;
            case PAST -> 1;
            case FUTURE -> 2;
        };
    }
}
