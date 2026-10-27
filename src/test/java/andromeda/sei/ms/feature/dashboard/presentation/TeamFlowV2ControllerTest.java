package andromeda.sei.ms.feature.dashboard.presentation;

import andromeda.sei.ms.feature.dashboard.application.dto.v2.DashboardV2Parts.SprintMeta;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.TeamFlowV2Output;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.TeamFlowV2Output.BranchCount;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.TeamFlowV2Output.FlowDay;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.TeamFlowV2Output.LabelCount;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.TeamFlowV2Output.ReviewQueue;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.TeamFlowV2Output.Rework;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.TeamFlowV2Output.Scope;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.TeamFlowV2Output.StageTimes;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.TeamFlowV2Output.StageTimesPoint;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.TeamFlowV2Output.StateTimes;
import andromeda.sei.ms.feature.dashboard.application.usecase.GetTeamFlowV2UseCase;
import andromeda.sei.ms.feature.sync.application.DataFreshnessResolver;
import andromeda.sei.ms.feature.sync.application.dto.DataFreshness;
import andromeda.sei.ms.feature.workitem.domain.exception.SprintNotFoundException;
import andromeda.sei.ms.shared.domain.metrics.DurationStat;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import andromeda.sei.ms.shared.presentation.handler.GlobalExceptionHandler;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * dashboards-customizaveis 1.13 — contract of {@code /api/v1/dashboards/team/flow}. Standalone
 * MockMvc ({@code @WebMvcTest} does not run under Boot 4 in this repo); {@code @PreAuthorize} is
 * proven apart in {@link TeamFlowV2ControllerSecurityTest}.
 */
@ExtendWith(MockitoExtension.class)
class TeamFlowV2ControllerTest {

    private static final String ORG = "org-1";
    private static final Instant AS_OF = Instant.parse("2026-10-11T12:00:00Z");

    @Mock private GetTeamFlowV2UseCase useCase;
    @Mock private DataFreshnessResolver dataFreshnessResolver;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        TeamFlowV2Controller controller = new TeamFlowV2Controller(useCase, new TeamFlowV2Mapper(), dataFreshnessResolver);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
        TenantContextHolder.set(ORG);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void should_return200WithTheFlowPayload_withThePreviewKeys_asOfAndDataFreshness() throws Exception {
        DurationStat stat = new DurationStat(8.0, 12.0, 2);
        when(useCase.execute(ORG, "p1", "s2")).thenReturn(new TeamFlowV2Output(
                new SprintMeta("s2", "Sprint 2", "Core", "2026-10-01", "2026-10-14", "CURRENT", 6, 10), null,
                List.of(), List.of(new FlowDay("2026-10-01", "01/10", 3, 2, 1)),
                new StateTimes(new StageTimes(stat, stat, null),
                        List.of(new StageTimesPoint("s2", "S2*", "Sprint 2", stat, stat, null))),
                new Rework(List.of(), 4, 25.0), new Scope(5.0),
                new ReviewQueue(3, 1, List.of(new BranchCount("main", 3)), List.of(new LabelCount("bug", 2))), AS_OF));
        when(dataFreshnessResolver.resolve(ORG, "p1")).thenReturn(new DataFreshness(AS_OF, false));

        mockMvc.perform(get("/api/v1/dashboards/team/flow").param("projectId", "p1").param("sprintId", "s2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sprint.timeFrame").value("CURRENT"))
                .andExpect(jsonPath("$.processTemplate").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.states").isEmpty())
                .andExpect(jsonPath("$.cfd[0].date").value("2026-10-01"))
                .andExpect(jsonPath("$.cfd[0].label").value("01/10"))
                .andExpect(jsonPath("$.cfd[0].Proposed").value(3))
                .andExpect(jsonPath("$.cfd[0].InProgress").value(2))
                .andExpect(jsonPath("$.cfd[0].Completed").value(1))
                .andExpect(jsonPath("$.cfd[0].Resolved").doesNotExist())
                .andExpect(jsonPath("$.cfd[0].proposed").doesNotExist())
                .andExpect(jsonPath("$.stateTimes.current.queueHours.p50").value(8.0))
                .andExpect(jsonPath("$.stateTimes.current.inProgressHours.n").value(2))
                .andExpect(jsonPath("$.stateTimes.current.resolvedHours").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.stateTimes.history[0].label").value("S2*"))
                .andExpect(jsonPath("$.rework.doneInWindow").value(4))
                .andExpect(jsonPath("$.rework.ratePct").value(25.0))
                .andExpect(jsonPath("$.scope.atRiskSp").doesNotExist())
                .andExpect(jsonPath("$.reviewQueue.open").value(3))
                .andExpect(jsonPath("$.reviewQueue.drafts").value(1))
                .andExpect(jsonPath("$.reviewQueue.byBaseBranch[0].branch").value("main"))
                .andExpect(jsonPath("$.reviewQueue.labels[0].label").value("bug"))
                .andExpect(jsonPath("$.reviewQueue.byReviewer").doesNotExist())
                .andExpect(jsonPath("$.board").doesNotExist())
                .andExpect(jsonPath("$.remainingWork").doesNotExist())
                .andExpect(jsonPath("$.handoffs").doesNotExist())
                .andExpect(jsonPath("$.bugs").doesNotExist())
                .andExpect(jsonPath("$.scopeHistory").doesNotExist())
                .andExpect(jsonPath("$.asOf").value("2026-10-11T12:00:00Z"))
                .andExpect(jsonPath("$.dataFreshness.refreshRunning").value(false));
    }

    @Test
    void should_return200WithNulls_when_projectHasNoSprint() throws Exception {
        when(useCase.execute(ORG, "p1", null)).thenReturn(TeamFlowV2Output.withoutSprint(AS_OF));

        mockMvc.perform(get("/api/v1/dashboards/team/flow").param("projectId", "p1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sprint").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.cfd").isEmpty())
                .andExpect(jsonPath("$.rework").value(Matchers.nullValue()));
    }

    @Test
    void should_answer404SprintNotFound_when_sprintIsOfAnotherProject() throws Exception {
        when(useCase.execute(any(), any(), eq("s-other"))).thenThrow(new SprintNotFoundException("s-other"));

        mockMvc.perform(get("/api/v1/dashboards/team/flow").param("projectId", "p1").param("sprintId", "s-other"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SPRINT_NOT_FOUND"));
    }
}
