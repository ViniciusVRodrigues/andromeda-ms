package andromeda.sei.ms.feature.dashboard.application.usecase;

import andromeda.sei.ms.feature.dashboard.application.dto.StrategicDashboardOutput;
import andromeda.sei.ms.feature.project.domain.Project;
import andromeda.sei.ms.feature.project.domain.ProjectRepository;
import andromeda.sei.ms.feature.user.domain.UserRepository;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.domain.WorkItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetStrategicDashboardUseCaseTest {

    @Mock private ProjectRepository projectRepository;
    @Mock private WorkItemRepository workItemRepository;
    @Mock private UserRepository userRepository;
    @Mock private GetDeveloperDashboardUseCase getDeveloperDashboardUseCase;

    private GetStrategicDashboardUseCase useCase;

    private static final String ORG = "org-1";
    private static final String PROJECT = "proj-1";

    @BeforeEach
    void setUp() {
        useCase = new GetStrategicDashboardUseCase(
                projectRepository, workItemRepository, userRepository, getDeveloperDashboardUseCase);
        lenient().when(userRepository.findAllByOrganizationId(anyString())).thenReturn(List.of());
    }

    @Test
    void should_includeFeatureTypedWorkItem_inProjectSummaryVelocity_notDropIt() {
        LocalDate from = LocalDate.now().minusDays(90);
        LocalDate to = LocalDate.now();
        Instant completedAt = to.atStartOfDay(ZoneOffset.UTC).toInstant().minusSeconds(3600);

        Project project = new Project(PROJECT, ORG, "My Project", "my-project", "desc",
                true, List.of(), 1L, Instant.now(), Instant.now());
        when(projectRepository.findAllActiveByOrganizationId(ORG)).thenReturn(List.of(project));

        WorkItem feature = new WorkItem("wi-feat", ORG, PROJECT, WorkItem.Source.AZURE_DEVOPS,
                "wi-feat", Instant.now(), "Feature Title",
                "Feature", WorkItem.ResolvedType.FEATURE,
                "Closed", WorkItem.ResolvedStatus.DONE,
                "Medium", 8.0, List.of(),
                null, "ext-user", "user-1", null, null,
                Instant.now(), null, completedAt,
                null, null, null, true, 1L, Instant.now(), null, null, null, null, 8.0, null, null, null);

        when(workItemRepository.findAllByOrganizationIdAndProjectId(ORG, PROJECT)).thenReturn(List.of(feature));

        StrategicDashboardOutput output = useCase.execute(ORG, "auth-user", null, null, from, to);

        assertThat(output.projects()).hasSize(1);
        // A FEATURE-typed item must be counted like a STORY here, not silently dropped (0 sp).
        assertThat(output.projects().get(0).storyPointsDelivered()).isEqualTo(8.0);
        // (uses WorkItem.getStoryPoints(), the dual-written legacy field, per buildProjectSummary)
    }
}
