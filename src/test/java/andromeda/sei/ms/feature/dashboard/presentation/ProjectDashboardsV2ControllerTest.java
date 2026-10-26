package andromeda.sei.ms.feature.dashboard.presentation;

import andromeda.sei.ms.feature.dashboard.application.dto.v2.ProjectOverviewV2Output;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.ProjectOverviewV2Output.Ai;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.ProjectOverviewV2Output.CostBucket;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.ProjectOverviewV2Output.ThroughputRow;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.ProjectQualityV2Output;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.ProjectRoadmapV2Output;
import andromeda.sei.ms.feature.dashboard.application.usecase.GetProjectOverviewV2UseCase;
import andromeda.sei.ms.feature.dashboard.application.usecase.GetProjectQualityV2UseCase;
import andromeda.sei.ms.feature.dashboard.application.usecase.GetProjectRoadmapV2UseCase;
import andromeda.sei.ms.feature.project.domain.exception.ProjectNotFoundException;
import andromeda.sei.ms.feature.sync.application.DataFreshnessResolver;
import andromeda.sei.ms.feature.sync.application.dto.DataFreshness;
import andromeda.sei.ms.shared.domain.metrics.DurationStat;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import andromeda.sei.ms.shared.presentation.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * dashboards-customizaveis 1.10 — contract of {@code /api/v1/dashboards/projects/{projectId}/
 * overview|roadmap|quality}: the period parameter (default 90, 422 otherwise), {@code asOf} and
 * {@code dataFreshness}, the serialized names the v2 preview reads, and {@code PROJECT_NOT_FOUND}
 * passing through as 404. Standalone MockMvc; {@code @PreAuthorize} is proven in
 * {@link ProjectDashboardsV2ControllerSecurityTest}.
 */
@ExtendWith(MockitoExtension.class)
class ProjectDashboardsV2ControllerTest {

    private static final String ORG = "org-1";
    private static final Instant AS_OF = Instant.parse("2026-10-11T12:00:00Z");
    private static final DurationStat EMPTY = DurationStat.empty();

    @Mock private GetProjectOverviewV2UseCase overviewUseCase;
    @Mock private GetProjectRoadmapV2UseCase roadmapUseCase;
    @Mock private GetProjectQualityV2UseCase qualityUseCase;
    @Mock private DataFreshnessResolver dataFreshnessResolver;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ProjectDashboardsV2Controller controller = new ProjectDashboardsV2Controller(overviewUseCase, roadmapUseCase,
                qualityUseCase, new ProjectDashboardsV2Mapper(), dataFreshnessResolver);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
        TenantContextHolder.set(ORG);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void should_return200WithTheOverview_defaulting90Days_withAsOfAndDataFreshness() throws Exception {
        when(overviewUseCase.execute(ORG, "p1", 90)).thenReturn(overview());
        when(dataFreshnessResolver.resolve(ORG, "p1")).thenReturn(new DataFreshness(AS_OF, false));

        mockMvc.perform(get("/api/v1/dashboards/projects/p1/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.asOf").value("2026-10-11T12:00:00Z"))
                .andExpect(jsonPath("$.dataFreshness.refreshRunning").value(false))
                .andExpect(jsonPath("$.period.granularity").value("semana"))
                .andExpect(jsonPath("$.throughput[0].STORY").value(2))
                .andExpect(jsonPath("$.throughput[0].BUG").value(1))
                .andExpect(jsonPath("$.throughput[0].story").doesNotExist())
                .andExpect(jsonPath("$.sprints[0].aiTokens").value(15000))
                .andExpect(jsonPath("$.doraSeries[0].start").value("2026-07-20"))
                .andExpect(jsonPath("$.doraSeries[0].leadTimeHoursP50").value(31.5))
                .andExpect(jsonPath("$.doraSeries[0].deployFreqPerWeek").value(4.0))
                .andExpect(jsonPath("$.doraSeries[0].changeFailurePct").value(nullValue()))
                .andExpect(jsonPath("$.doraSeries[0].method").value("VCS_PROXY"))
                .andExpect(jsonPath("$.doraSeries[0].estimated").value(false))
                .andExpect(jsonPath("$.ai.costByBucket[0].byModel.claude").value(1.5))
                .andExpect(jsonPath("$.kpis.leadTimeHours.n").value(0))
                .andExpect(jsonPath("$.kpis.spDelivered").value(nullValue()))
                .andExpect(jsonPath("$.previous.stalePrs").value(nullValue()))
                .andExpect(jsonPath("$.dora.leadTime.hours.p50").value(nullValue()));
    }

    @Test
    void should_return200WithTheRoadmap_progressFlattenedIntoEachRow() throws Exception {
        when(roadmapUseCase.execute(ORG, "p1", 180)).thenReturn(roadmap());

        mockMvc.perform(get("/api/v1/dashboards/projects/p1/roadmap").param("periodDays", "180"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.epics[0].externalId").value("E-1"))
                .andExpect(jsonPath("$.epics[0].pctDone").value(50.0))
                .andExpect(jsonPath("$.epics[0].forecastWeeks").value(12.8))
                .andExpect(jsonPath("$.epics[0].progress").doesNotExist())
                .andExpect(jsonPath("$.calibration.byEffort[0].aiCostUsd").value(2.5))
                .andExpect(jsonPath("$.calibration.byEffort[0].aiTokens").value(15000))
                .andExpect(jsonPath("$.calibration.byEffort[0].itemsWithAi").value(1))
                .andExpect(jsonPath("$.calibration.byComplexity[0].aiCostUsd").value(nullValue()))
                .andExpect(jsonPath("$.calibration.byComplexity[0].aiTokens").value(nullValue()))
                .andExpect(jsonPath("$.calibration.byComplexity[0].itemsWithAi").value(0))
                .andExpect(jsonPath("$.config.effortBearingLevel.inferred").value("STORY"))
                .andExpect(jsonPath("$.asOf").value("2026-10-11T12:00:00Z"));
    }

    @Test
    void should_return200WithQuality() throws Exception {
        when(qualityUseCase.execute(ORG, "p1", 365)).thenReturn(quality());

        mockMvc.perform(get("/api/v1/dashboards/projects/p1/quality").param("periodDays", "365"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kpis.resolutionHours.p90").value(100.0))
                .andExpect(jsonPath("$.commitKinds[0].fix").value(3))
                .andExpect(jsonPath("$.commitKindTotals[0].kind").value("feat"))
                .andExpect(jsonPath("$.totals.commits").value(3));
        verify(qualityUseCase).execute(ORG, "p1", 365);
    }

    @ParameterizedTest
    @ValueSource(strings = {"45", "abc", "0"})
    void should_answer422DashboardPeriodInvalid_forAPeriodOutsideTheSet(String periodDays) throws Exception {
        for (String view : List.of("overview", "roadmap", "quality")) {
            mockMvc.perform(get("/api/v1/dashboards/projects/p1/" + view).param("periodDays", periodDays))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("DASHBOARD_PERIOD_INVALID"));
        }
        verifyNoInteractions(overviewUseCase, roadmapUseCase, qualityUseCase);
    }

    @Test
    void should_answer404ProjectNotFound_unwrapped() throws Exception {
        when(qualityUseCase.execute(anyString(), anyString(), anyInt())).thenThrow(new ProjectNotFoundException("p9"));

        mockMvc.perform(get("/api/v1/dashboards/projects/p9/quality"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"));
    }

    private static ProjectOverviewV2Output overview() {
        var stats = new ProjectOverviewV2Output.PeriodStats(3, null, null, null, EMPTY, EMPTY, null, null,
                BigDecimal.ZERO, null, 0, EMPTY, EMPTY, 0, 0);
        var previous = new ProjectOverviewV2Output.PeriodStats(0, null, null, null, EMPTY, EMPTY, null, null,
                BigDecimal.ZERO, null, 0, EMPTY, EMPTY, null, 0);
        return new ProjectOverviewV2Output(
                new ProjectOverviewV2Output.ProjectInfo("p1", "Orion", null, 0, List.of()),
                new ProjectOverviewV2Output.Period(90, "2026-07-14", "2026-10-11", "2026-04-15", "2026-07-13", "semana"),
                stats, previous,
                new ProjectOverviewV2Output.Dora("UNAVAILABLE", new ProjectOverviewV2Output.LeadTime(EMPTY, null),
                        new ProjectOverviewV2Output.DeployFrequency(null, null, true),
                        new ProjectOverviewV2Output.ChangeFailureRate(null, null, true)),
                List.of(new ProjectOverviewV2Output.DoraRow("2026-07-20", "20/07", 31.5, 4.0, null, "VCS_PROXY", false)),
                List.of(new ThroughputRow("2026-07-20", "20/07", 2, 1, 0)),
                List.of(new ProjectOverviewV2Output.SprintRow("s1", "S1", "Sprint 1", 10.0, 8.0, 80.0,
                        BigDecimal.ONE, null, 15000L)),
                List.of(), List.of(), List.of(),
                List.of(),
                new Ai(List.of("claude"), List.of(new CostBucket("2026-07-20", "20/07", Map.of("claude", new BigDecimal("1.5")))),
                        new ProjectOverviewV2Output.Adoption(0, 0),
                        new ProjectOverviewV2Output.Subscription(BigDecimal.ZERO, null, null), null),
                new ProjectOverviewV2Output.People(0, 0, 0, 0),
                new ProjectOverviewV2Output.DataQuality(null, null, null), AS_OF);
    }

    private static ProjectRoadmapV2Output roadmap() {
        var progress = new ProjectRoadmapV2Output.Progress(16.0, 8.0, 50.0, 3, 2, 0, 8.0, 0.625, 12.8, AS_OF);
        return new ProjectRoadmapV2Output(new ProjectRoadmapV2Output.Period(180, "2026-04-15", "2026-10-11"),
                new ProjectRoadmapV2Output.Config(null, new ProjectRoadmapV2Output.EffortLevel("AUTO", "STORY")),
                List.of(new ProjectRoadmapV2Output.EpicRow("E-1", "Épico", "IN_PROGRESS", List.of(), 1, 0, progress)),
                List.of(),
                new ProjectRoadmapV2Output.Backlog(0, null, null, null, List.of(), List.of(), List.of(), List.of()),
                List.of(), new ProjectRoadmapV2Output.Calibration(
                        List.of(new ProjectRoadmapV2Output.EffortRow("3 SP", 3.0, 2, EMPTY, new BigDecimal("2.5"), 15000L, 1)),
                        List.of(new ProjectRoadmapV2Output.ComplexityRow("2", 2.0, 2, EMPTY, 3.0, null, null, 0)),
                        List.of(), null), AS_OF);
    }

    private static ProjectQualityV2Output quality() {
        return new ProjectQualityV2Output(new ProjectQualityV2Output.Period(365, "2025-10-12", "2026-10-11", "mês"),
                new ProjectQualityV2Output.Kpis(0, 0, 0, new DurationStat(20.0, 100.0, 4), null, 0, null, 0, 0, null),
                List.of(), List.of(), List.of(),
                List.of(new ProjectQualityV2Output.CommitKindRow("2025-11-01", "nov/25", 0, 3, 0, 0, 0, 0, 0)),
                List.of(new ProjectQualityV2Output.CommitKindTotal("feat", 0)),
                new ProjectQualityV2Output.Totals(3), AS_OF);
    }
}
