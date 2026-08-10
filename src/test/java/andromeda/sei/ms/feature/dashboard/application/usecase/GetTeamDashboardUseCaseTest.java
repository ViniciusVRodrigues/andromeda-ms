package andromeda.sei.ms.feature.dashboard.application.usecase;

import andromeda.sei.ms.feature.aiusage.domain.AIUsageRepository;
import andromeda.sei.ms.feature.dashboard.application.dto.TeamDashboardOutput;
import andromeda.sei.ms.feature.user.domain.User;
import andromeda.sei.ms.feature.user.domain.UserRepository;
import andromeda.sei.ms.feature.workitem.domain.Sprint;
import andromeda.sei.ms.feature.workitem.domain.SprintRepository;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.domain.WorkItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetTeamDashboardUseCaseTest {

    @Mock private SprintRepository sprintRepository;
    @Mock private WorkItemRepository workItemRepository;
    @Mock private AIUsageRepository aiUsageRepository;
    @Mock private UserRepository userRepository;

    private GetTeamDashboardUseCase useCase;

    private static final String ORG = "org-1";
    private static final String PROJECT = "proj-1";
    private static final String SPRINT_ID = "sprint-1";
    private static final Instant NOW = Instant.now();

    @BeforeEach
    void setUp() {
        useCase = new GetTeamDashboardUseCase(sprintRepository, workItemRepository, new andromeda.sei.ms.feature.workitem.application.usecase.EffortBearingLevelResolver(), aiUsageRepository, userRepository);
        lenient().when(userRepository.findAllByOrganizationId(anyString())).thenReturn(List.of());
        lenient().when(aiUsageRepository.findTotalCostByUserIdsAndDateRange(any(), any(), any(), any()))
                .thenReturn(Map.of());
    }

    @Test
    void should_includeFeatureTypedWorkItem_inMemberVelocity_notDropIt() {
        Sprint sprint = new Sprint(SPRINT_ID, ORG, PROJECT, WorkItem.Source.AZURE_DEVOPS,
                "ext-1", "team-1", "Team A", "Sprint 23", "/path",
                NOW.minusSeconds(864000), NOW.plusSeconds(86400),
                Sprint.TimeFrame.CURRENT, true, 1L, NOW, NOW);
        when(sprintRepository.findById(SPRINT_ID))
                .thenReturn(Optional.of(sprint));
        when(sprintRepository.findAllByOrganizationIdAndProjectId(ORG, PROJECT))
                .thenReturn(List.of(sprint));

        WorkItem feature = new WorkItem("wi-feat", ORG, PROJECT, WorkItem.Source.AZURE_DEVOPS,
                "wi-feat", NOW, "Feature Title",
                "Feature", WorkItem.ResolvedType.FEATURE,
                "Closed", WorkItem.ResolvedStatus.DONE,
                "Medium", 8.0, List.of(),
                SPRINT_ID, "ext-user", "user-1", null, null,
                NOW, null, NOW,
                null, null, null, true, 1L, NOW, null, null, null, null, 8.0, null, null, null);

        when(workItemRepository.findAllByOrganizationIdAndProjectIdAndSprintId(ORG, PROJECT, SPRINT_ID))
                .thenReturn(List.of(feature));
        when(userRepository.findAllByOrganizationId(ORG))
                .thenReturn(List.of(new User("user-1", ORG, "dev1@example.com", "hash",
                        "Dev One", null, null, null, null, null,
                        null, true, true, 1L, NOW, NOW)));

        TeamDashboardOutput output = useCase.execute(ORG, "auth-user", PROJECT, SPRINT_ID, null, null);

        // A FEATURE-typed item must be counted like a STORY here, not silently dropped (0 members).
        assertThat(output.members()).hasSize(1);
        assertThat(output.members().get(0).storyPointsDelivered()).isEqualTo(8.0);
        assertThat(output.teamVelocity()).isEqualTo(8.0);
    }
}
