package andromeda.sei.ms.feature.dashboard.application.usecase;

import andromeda.sei.ms.feature.aiusage.domain.AIUsage;
import andromeda.sei.ms.feature.aiusage.domain.AIUsageRepository;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocationRepository;
import andromeda.sei.ms.feature.allocation.domain.AllocationSplit;
import andromeda.sei.ms.feature.azure.domain.AzureProjectBinding;
import andromeda.sei.ms.feature.azure.domain.AzureProjectBindingRepository;
import andromeda.sei.ms.feature.azure.domain.FieldMapping;
import andromeda.sei.ms.feature.azure.domain.WorkflowConfig;
import andromeda.sei.ms.feature.connector.domain.ConnectorTokenRepository;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.ProjectRoadmapV2Output;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.ProjectRoadmapV2Output.EpicRow;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.ProjectRoadmapV2Output.FeatureRow;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.ProjectRoadmapV2Output.ManualOverride;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.ProjectRoadmapV2Output.PriorityRow;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.ProjectRoadmapV2Output.TagShare;
import andromeda.sei.ms.feature.dashboard.application.v2.DashboardPeopleDirectory;
import andromeda.sei.ms.feature.project.domain.Project;
import andromeda.sei.ms.feature.project.domain.ProjectRepository;
import andromeda.sei.ms.feature.project.domain.exception.ProjectNotFoundException;
import andromeda.sei.ms.feature.user.domain.User;
import andromeda.sei.ms.feature.user.domain.UserRepository;
import andromeda.sei.ms.feature.workitem.application.usecase.EffortBearingLevelResolver;
import andromeda.sei.ms.feature.workitem.domain.FieldValueSource;
import andromeda.sei.ms.feature.workitem.domain.Sprint;
import andromeda.sei.ms.feature.workitem.domain.SprintRepository;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.domain.WorkItem.ResolvedStatus;
import andromeda.sei.ms.feature.workitem.domain.WorkItem.ResolvedType;
import andromeda.sei.ms.feature.workitem.domain.WorkItemFixture;
import andromeda.sei.ms.feature.workitem.domain.WorkItemRepository;
import andromeda.sei.ms.shared.domain.metrics.DurationStat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Duration;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * dashboards-customizaveis 1.8 — project roadmap v2, table tests ported from the preview's
 * {@code buildRoadmap}: epic/feature progress by the hierarchy, the forecast from the last 8 weeks'
 * pace, backlog health (staleness by the Azure change date, priority, tags), calibration and manual
 * overrides. Now = 2026-10-11 12:00Z; 90 days = 2026-07-14 → 2026-10-11.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GetProjectRoadmapV2UseCaseTest {

    private static final String ORG = WorkItemFixture.ORG;
    private static final String PROJECT = WorkItemFixture.PROJECT;
    private static final Instant NOW = Instant.parse("2026-10-11T12:00:00Z");

    @Mock private ProjectRepository projectRepository;
    @Mock private WorkItemRepository workItemRepository;
    @Mock private SprintRepository sprintRepository;
    @Mock private AzureProjectBindingRepository bindingRepository;
    @Mock private andromeda.sei.ms.feature.azure.domain.AzureConnectionRepository connectionRepository;
    @Mock private UserRepository userRepository;
    @Mock private ConnectorTokenRepository connectorTokenRepository;
    @Mock private AiUsageAllocationRepository allocationRepository;
    @Mock private AIUsageRepository aiUsageRepository;

    private final List<WorkItem> items = new ArrayList<>();
    private GetProjectRoadmapV2UseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetProjectRoadmapV2UseCase(projectRepository, workItemRepository, sprintRepository,
                new EffortBearingLevelResolver(), bindingRepository, connectionRepository,
                new DashboardPeopleDirectory(userRepository, connectorTokenRepository), allocationRepository, aiUsageRepository,
                Clock.fixed(NOW, ZoneOffset.UTC));
        when(projectRepository.findActiveByIdAndOrganizationId(PROJECT, ORG)).thenReturn(Optional.of(
                new Project(PROJECT, ORG, "Orion", "orion", null, true, List.of("user-a"), 0L, NOW, NOW)));
        when(workItemRepository.findAllByOrganizationIdAndProjectId(ORG, PROJECT)).thenReturn(items);
    }

    @Test
    void should_throwProjectNotFound_when_projectIsNotOfTheOrganization() {
        assertThatThrownBy(() -> useCase.execute("org-2", PROJECT, 90)).isInstanceOf(ProjectNotFoundException.class);
    }

    @Test
    void should_reportComplexityDoneInThePeriod_nextToTheEffortOne() {
        WorkItem c1 = child("c1", 3.0, ResolvedStatus.DONE, "f1", daysAgo(10));
        c1.setComplexity(2.0);
        WorkItem c2 = child("c2", 5.0, ResolvedStatus.DONE, "f1", daysAgo(200));
        c2.setComplexity(5.0);
        WorkItem c3 = child("c3", 8.0, ResolvedStatus.TODO, "f1", null);
        c3.setComplexity(1.0);
        items.addAll(List.of(node("e1", ResolvedType.EPIC, ResolvedStatus.IN_PROGRESS, null),
                node("f1", ResolvedType.FEATURE, ResolvedStatus.IN_PROGRESS, "e1"), c1, c2, c3));

        ProjectRoadmapV2Output out = useCase.execute(ORG, PROJECT, 90);

        assertThat(out.features().getFirst().progress().complexityDoneInPeriod()).isEqualTo(2.0);
        assertThat(out.epics().getFirst().progress().complexityDoneInPeriod()).isEqualTo(2.0);
    }

    @Test
    void should_leaveComplexityDoneInThePeriodNull_when_noChildCarriesComplexity() {
        items.addAll(List.of(node("e1", ResolvedType.EPIC, ResolvedStatus.IN_PROGRESS, null),
                node("f1", ResolvedType.FEATURE, ResolvedStatus.IN_PROGRESS, "e1"),
                child("c1", 3.0, ResolvedStatus.DONE, "f1", daysAgo(10))));

        ProjectRoadmapV2Output out = useCase.execute(ORG, PROJECT, 90);

        assertThat(out.features().getFirst().progress().complexityDoneInPeriod()).isNull();
    }

    @Test
    void should_measureFeatureProgress_andForecastFromTheLast8WeeksPace() {
        items.addAll(List.of(node("e1", ResolvedType.EPIC, ResolvedStatus.IN_PROGRESS, null),
                node("f1", ResolvedType.FEATURE, ResolvedStatus.IN_PROGRESS, "e1"),
                child("c1", 3.0, ResolvedStatus.DONE, "f1", daysAgo(70)),
                child("c2", 5.0, ResolvedStatus.DONE, "f1", daysAgo(10)),
                child("c3", 8.0, ResolvedStatus.TODO, "f1", null),
                child("d1", null, ResolvedStatus.TODO, "e1", null)));

        ProjectRoadmapV2Output out = useCase.execute(ORG, PROJECT, 90);

        FeatureRow f1 = out.features().getFirst();
        assertThat(f1.epic()).isEqualTo("Item e1");
        assertThat(f1.progress().effortTotal()).isEqualTo(16.0);
        assertThat(f1.progress().effortDone()).isEqualTo(8.0);
        assertThat(f1.progress().pctDone()).isEqualTo(50.0);
        assertThat(f1.progress().itemsDone()).isEqualTo(2);
        assertThat(f1.progress().weeklyPace()).isEqualTo(0.625);
        assertThat(f1.progress().forecastWeeks()).isCloseTo(12.8, within(1e-9));
        EpicRow e1 = out.epics().getFirst();
        assertThat(e1.featureCount()).isEqualTo(1);
        assertThat(e1.featuresDone()).isZero();
        assertThat(e1.progress().itemsTotal()).isEqualTo(4);
        assertThat(e1.progress().unestimated()).isEqualTo(1);
        assertThat(e1.progress().effortTotal()).isEqualTo(16.0);
    }

    @Test
    void should_forecastZero_whenNothingRemains_andNull_withoutRecentPaceOrEffort() {
        items.addAll(List.of(node("f2", ResolvedType.FEATURE, ResolvedStatus.DONE, null),
                child("x1", 2.0, ResolvedStatus.DONE, "f2", daysAgo(100)),
                node("f3", ResolvedType.FEATURE, ResolvedStatus.TODO, null),
                child("x2", 3.0, ResolvedStatus.TODO, "f3", null),
                node("f4", ResolvedType.FEATURE, ResolvedStatus.TODO, null)));

        ProjectRoadmapV2Output out = useCase.execute(ORG, PROJECT, 90);

        assertThat(feature(out, "f2").progress().forecastWeeks()).isZero();
        assertThat(feature(out, "f2").progress().weeklyPace()).isNull();
        assertThat(feature(out, "f3").progress().forecastWeeks()).isNull();
        assertThat(feature(out, "f4").progress().effortTotal()).isNull();
        assertThat(feature(out, "f4").progress().pctDone()).isNull();
        assertThat(feature(out, "f4").progress().lastActivityAt()).isNull();
        assertThat(feature(out, "f4").progress().forecastWeeks()).isNull();
        // not done first, then the most recent activity; f2 (done) last
        assertThat(out.features()).extracting(FeatureRow::externalId).last().isEqualTo("ext-f2");
    }

    @Test
    void should_measureBacklogHealth_byStalenessPriorityAndTags() {
        when(sprintRepository.findAllByOrganizationIdAndProjectId(ORG, PROJECT)).thenReturn(List.of(
                sprint("s-cur", Sprint.TimeFrame.CURRENT, "2026-10-05", "2026-10-16"),
                sprint("s-fut", Sprint.TimeFrame.FUTURE, "2026-10-19", "2026-10-30")));
        items.addAll(List.of(backlog("b1", 3.0, "1", daysAgo(10), List.of("api")),
                parent(backlog("b2", null, "2", daysAgo(70), List.of()), "ext-f9"),
                backlog("b3", 2.0, "1", daysAgo(100), List.of("api", "ui")),
                inSprint(backlog("b4", null, "3", daysAgo(40), List.of()), "s-fut"),
                inSprint(backlog("n1", 5.0, "1", daysAgo(1), List.of()), "s-cur"),
                WorkItemFixture.item("n2", ResolvedType.STORY, ResolvedStatus.DONE),
                node("n3", ResolvedType.EPIC, ResolvedStatus.TODO, null)));

        ProjectRoadmapV2Output.Backlog backlog = useCase.execute(ORG, PROJECT, 90).backlog();

        assertThat(backlog.count()).isEqualTo(4);
        assertThat(backlog.points()).isEqualTo(5.0);
        assertThat(backlog.unestimatedPct()).isEqualTo(50.0);
        assertThat(backlog.withoutParentPct()).isEqualTo(75.0);
        assertThat(backlog.staleness()).extracting(ProjectRoadmapV2Output.StaleBand::count).containsExactly(1, 1, 1, 1);
        assertThat(backlog.byPriority()).containsExactly(new PriorityRow(1, 2, 1), new PriorityRow(2, 1, 1),
                new PriorityRow(3, 1, 0), new PriorityRow(4, 0, 0));
        assertThat(backlog.forgotten().getFirst().externalId()).isEqualTo("ext-b3");
        assertThat(backlog.forgotten().getFirst().daysWithoutChange()).isEqualTo(100.0);
        assertThat(backlog.forgotten().getFirst().priority()).isEqualTo(1);
        assertThat(backlog.byTag()).containsExactly(new TagShare("api", 2, 5.0), new TagShare("ui", 1, 2.0));
    }

    @Test
    void should_calibrateCycleTimeBySizeAndComplexity_andListManualOverrides() {
        when(sprintRepository.findAllByOrganizationIdAndProjectId(ORG, PROJECT)).thenReturn(List.of(
                sprint("s1", Sprint.TimeFrame.PAST, "2026-09-01", "2026-09-14")));
        when(userRepository.findAllByOrganizationId(ORG)).thenReturn(List.of(new User("user-a", ORG, "a@x.io", null,
                "Ana", "role", null, null, null, null, null, true, true, 0L, NOW, NOW)));
        WorkItem c1 = inSprint(calibrated("c1", 3.0, 2.0, 10.0, List.of("api")), "s1");
        c1.setComplexitySource(FieldValueSource.MANUAL_OVERRIDE);
        c1.setComplexityOverriddenBy("user-z");
        c1.setComplexityOverriddenAt(Instant.parse("2026-09-10T00:00:00Z"));
        WorkItem c2 = inSprint(calibrated("c2", 3.0, 2.0, 30.0, List.of()), "s1");
        WorkItem c3 = inSprint(calibrated("c3", 5.0, 5.0, 50.0, List.of()), "s1");
        c3.setEffortSource(FieldValueSource.MANUAL_OVERRIDE);
        c3.setEffortOverriddenBy("user-a");
        c3.setEffortOverriddenAt(Instant.parse("2026-09-01T00:00:00Z"));
        items.addAll(List.of(c1, c2, c3));

        ProjectRoadmapV2Output out = useCase.execute(ORG, PROJECT, 90);

        assertThat(out.calibration().byEffort()).hasSize(2);
        assertThat(out.calibration().byEffort().getFirst())
                .isEqualTo(new ProjectRoadmapV2Output.EffortRow("3", 3.0, 2, new DurationStat(10.0, 30.0, 2), null, null, 0));
        assertThat(out.calibration().byComplexity().getFirst().label()).isEqualTo("2");
        assertThat(out.calibration().byComplexity().getFirst().avgEffort()).isEqualTo(3.0);
        assertThat(out.calibration().overrides()).extracting(ManualOverride::field, ManualOverride::by)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("Complexidade", "user-z"),
                        org.assertj.core.groups.Tuple.tuple("Esforço", "Ana"));
        assertThat(out.calibration().overridePct()).isEqualTo(66.67);
        assertThat(out.deliveredByTag()).containsExactly(new TagShare("api", 1, 2.0));
    }

    @Test
    void should_addAllocatedAiToEachBand_withPartialSplitAndNullWhenNothingIsAllocated() {
        when(sprintRepository.findAllByOrganizationIdAndProjectId(ORG, PROJECT)).thenReturn(List.of(
                sprint("s1", Sprint.TimeFrame.PAST, "2026-09-01", "2026-09-14")));
        WorkItem c1 = inSprint(calibrated("c1", 3.0, 2.0, 10.0, List.of()), "s1");
        WorkItem c2 = inSprint(calibrated("c2", 3.0, 2.0, 30.0, List.of()), "s1");
        WorkItem c3 = inSprint(calibrated("c3", 5.0, 5.0, 50.0, List.of()), "s1");
        items.addAll(List.of(c1, c2, c3));
        AiUsageAllocation partial = new AiUsageAllocation("al1", ORG, "user-a", java.time.LocalDate.parse("2026-09-05"),
                List.of("u1"), List.of(new AllocationSplit("c1", "Item", new BigDecimal("0.8")),
                new AllocationSplit("elsewhere", "Other", new BigDecimal("0.2"))),
                AiUsageAllocation.Status.VALIDATED, AiUsageAllocation.AssignedBy.MANUAL, true, 0L, NOW, NOW);
        when(allocationRepository.findValidatedByOrganizationIdAndWorkItemIdIn(eq(ORG), anyCollection()))
                .thenReturn(List.of(partial));
        when(aiUsageRepository.findCostFieldsByOrganizationIdAndIdIn(eq(ORG), anyCollection())).thenReturn(List.of(
                new AIUsage("u1", ORG, "user-a", "gh", "ds", java.time.LocalDate.parse("2026-09-05"), "claude", "w", "w",
                        "m", "m", 600, 400, 2, "vscode", "al1", new BigDecimal("10.00"), NOW, true, 0L, NOW, NOW)));

        ProjectRoadmapV2Output out = useCase.execute(ORG, PROJECT, 90);

        var effort3 = out.calibration().byEffort().get(0);
        assertThat(effort3.aiCostUsd()).isEqualByComparingTo("8");
        assertThat(effort3.aiTokens()).isEqualTo(800L);
        assertThat(effort3.itemsWithAi()).isEqualTo(1);
        var effort5 = out.calibration().byEffort().get(1);
        assertThat(effort5.aiCostUsd()).isNull();
        assertThat(effort5.aiTokens()).isNull();
        assertThat(effort5.itemsWithAi()).isZero();
        var complexity2 = out.calibration().byComplexity().get(0);
        assertThat(complexity2.aiCostUsd()).isEqualByComparingTo("8");
        assertThat(complexity2.aiTokens()).isEqualTo(800L);
        assertThat(complexity2.itemsWithAi()).isEqualTo(1);
        assertThat(out.calibration().byComplexity().get(1).itemsWithAi()).isZero();
    }

    @Test
    void should_reportNoAi_when_noBandHasAllocation() {
        when(sprintRepository.findAllByOrganizationIdAndProjectId(ORG, PROJECT)).thenReturn(List.of(
                sprint("s1", Sprint.TimeFrame.PAST, "2026-09-01", "2026-09-14")));
        items.add(inSprint(calibrated("c1", 3.0, 2.0, 10.0, List.of()), "s1"));
        when(allocationRepository.findValidatedByOrganizationIdAndWorkItemIdIn(eq(ORG), anyCollection())).thenReturn(List.of());

        ProjectRoadmapV2Output out = useCase.execute(ORG, PROJECT, 90);

        assertThat(out.calibration().byEffort().getFirst().aiCostUsd()).isNull();
        assertThat(out.calibration().byEffort().getFirst().aiTokens()).isNull();
        assertThat(out.calibration().byEffort().getFirst().itemsWithAi()).isZero();
    }

    @Test
    void should_reportTheEffortField_andTheInferredLevel() {
        when(bindingRepository.findByOrganizationIdAndProjectIdAndActiveTrue(ORG, PROJECT)).thenReturn(Optional.of(
                new AzureProjectBinding("b1", ORG, PROJECT, "az", "Orion", true, NOW,
                        new WorkflowConfig(List.of(), List.of(),
                                List.of(new FieldMapping(FieldMapping.InternalFieldTarget.EFFORT, "Custom.Effort", "double")),
                                null, WorkflowConfig.ConfigSource.CUSTOM),
                        true, NOW, NOW, 0L)));

        ProjectRoadmapV2Output out = useCase.execute(ORG, PROJECT, 90);

        assertThat(out.config().effortField()).isEqualTo("Custom.Effort");
        assertThat(out.config().effortBearingLevel()).isEqualTo(new ProjectRoadmapV2Output.EffortLevel("AUTO", "STORY"));
    }

    @Test
    void should_reportThePerTypeEffortField_andTheComplexityField_fromTheBinding() {
        when(bindingRepository.findByOrganizationIdAndProjectIdAndActiveTrue(ORG, PROJECT)).thenReturn(Optional.of(
                new AzureProjectBinding("b1", ORG, PROJECT, "az", "Orion", true, NOW,
                        new WorkflowConfig(List.of(), List.of(),
                                List.of(new FieldMapping(FieldMapping.InternalFieldTarget.EFFORT, "Task",
                                                "Microsoft.VSTS.Scheduling.CompletedWork", "double"),
                                        new FieldMapping(FieldMapping.InternalFieldTarget.COMPLEXITY,
                                                "Custom.Points", "double")),
                                null, WorkflowConfig.ConfigSource.CUSTOM),
                        true, NOW, NOW, 0L)));

        ProjectRoadmapV2Output out = useCase.execute(ORG, PROJECT, 90);

        assertThat(out.config().effortField()).isEqualTo("Microsoft.VSTS.Scheduling.CompletedWork");
        assertThat(out.config().complexityField()).isEqualTo("Custom.Points");
    }

    @Test
    void should_reportTheDefaultFields_when_theBindingHasNoWorkflowConfig_andNullWithoutBinding() {
        assertThat(useCase.execute(ORG, PROJECT, 90).config().effortField()).isNull();
        assertThat(useCase.execute(ORG, PROJECT, 90).config().complexityField()).isNull();
        when(bindingRepository.findByOrganizationIdAndProjectIdAndActiveTrue(ORG, PROJECT)).thenReturn(Optional.of(
                new AzureProjectBinding("b1", ORG, PROJECT, "az", "Orion", true, NOW, null, true, NOW, NOW, 0L)));

        ProjectRoadmapV2Output out = useCase.execute(ORG, PROJECT, 90);

        assertThat(out.config().effortField()).isEqualTo(WorkflowConfig.DEFAULT_EFFORT_FIELD_REFERENCE_NAME);
        assertThat(out.config().complexityField()).isEqualTo(WorkflowConfig.DEFAULT_COMPLEXITY_FIELD_REFERENCE_NAME);
    }

    @Test
    void should_returnEmptyListsAndNulls_when_theProjectHasNoItems() {
        ProjectRoadmapV2Output out = useCase.execute(ORG, PROJECT, 365);

        assertThat(out.period().days()).isEqualTo(365);
        assertThat(out.epics()).isEmpty();
        assertThat(out.features()).isEmpty();
        assertThat(out.backlog().count()).isZero();
        assertThat(out.backlog().points()).isNull();
        assertThat(out.backlog().unestimatedPct()).isNull();
        assertThat(out.backlog().staleness()).hasSize(4).allSatisfy(b -> assertThat(b.count()).isZero());
        assertThat(out.backlog().forgotten()).isEmpty();
        assertThat(out.calibration().byEffort()).isEmpty();
        assertThat(out.calibration().overridePct()).isNull();
        assertThat(out.config().effortField()).isNull();
        assertThat(out.asOf()).isEqualTo(NOW);
    }


    /** Story of complexity 3 delivered 2026-09-12 with one Task child of {@code hours}. */
    private void storyWithHours(String id, double complexity, Double hours) {
        WorkItem story = calibrated(id, null, complexity, 10.0, List.of());
        items.add(story);
        if (hours != null) {
            WorkItem task = WorkItemFixture.item("t-" + id, ResolvedType.TASK, ResolvedStatus.DONE, hours, "ext-" + id);
            task.setCompletedAt(story.getCompletedAt());
            items.add(task);
        }
    }

    @Test
    void should_reportTheHoursOfEachComplexityBand_andTheOutliersOutsideHalfToDoubleTheMedian() {
        storyWithHours("a", 3.0, 6.0);
        storyWithHours("b", 3.0, 8.0);
        storyWithHours("c", 3.0, 30.0);
        storyWithHours("d", 3.0, null);
        storyWithHours("e", 5.0, 10.0);

        ProjectRoadmapV2Output out = useCase.execute(ORG, PROJECT, 90);

        var band3 = out.calibration().byComplexity().stream().filter(r -> r.complexity() == 3.0).findFirst().orElseThrow();
        assertThat(band3.effortHours().p50()).isEqualTo(8.0);
        assertThat(band3.effortHours().p25()).isEqualTo(6.0);
        assertThat(band3.effortHours().p75()).isEqualTo(30.0);
        assertThat(band3.effortHours().n()).isEqualTo(3);
        assertThat(band3.unitsWithoutEffort()).isEqualTo(1);
        assertThat(out.calibration().outliers()).extracting(ProjectRoadmapV2Output.Outlier::externalId,
                        ProjectRoadmapV2Output.Outlier::complexity, ProjectRoadmapV2Output.Outlier::effortHours)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("ext-c", 3.0, 30.0));
    }

    @Test
    void should_keepAtMostFiveOutliers_largestDeviationFirst_andLinkThemToAzure() {
        // no connection: the url stays null
        // median 4 h: seven items at 1 h (4x below... ratio .25) and 4 h core
        for (int i = 0; i < 8; i++) storyWithHours("n" + i, 2.0, 4.0);
        for (int i = 0; i < 6; i++) storyWithHours("h" + i, 2.0, 10.0 + i); // above 2x the median 4? band median = 4
        ProjectRoadmapV2Output out = useCase.execute(ORG, PROJECT, 90);

        assertThat(out.calibration().outliers()).hasSize(5);
        assertThat(out.calibration().outliers().getFirst().effortHours()).isEqualTo(15.0);
        assertThat(out.calibration().outliers()).extracting(ProjectRoadmapV2Output.Outlier::effortHours)
                .isSortedAccordingTo(java.util.Comparator.reverseOrder());
        assertThat(out.calibration().outliers().getFirst().url()).isNull();
    }

    @Test
    void should_buildTheOutlierUrlFromTheAzureOrganizationAndProject() {
        var connection = org.mockito.Mockito.mock(andromeda.sei.ms.feature.azure.domain.AzureConnection.class);
        when(connection.getAzureOrgUrl()).thenReturn("https://dev.azure.com/acme/");
        when(connectionRepository.findByOrganizationIdAndActiveTrue(ORG)).thenReturn(Optional.of(connection));
        var binding = org.mockito.Mockito.mock(AzureProjectBinding.class);
        when(binding.getAzureProjectName()).thenReturn("Meu Projeto");
        when(bindingRepository.findByOrganizationIdAndProjectIdAndActiveTrue(ORG, PROJECT)).thenReturn(Optional.of(binding));
        storyWithHours("a", 3.0, 6.0);
        storyWithHours("b", 3.0, 6.0);
        storyWithHours("c", 3.0, 30.0);

        ProjectRoadmapV2Output out = useCase.execute(ORG, PROJECT, 90);

        assertThat(out.calibration().outliers().getFirst().url())
                .isEqualTo("https://dev.azure.com/acme/Meu%20Projeto/_workitems/edit/ext-c");
    }

    @Test
    void should_notFlagOutliers_when_theBandMedianIsZeroOrThereIsNoTaskHours() {
        storyWithHours("a", 3.0, null);
        storyWithHours("b", 3.0, null);

        ProjectRoadmapV2Output out = useCase.execute(ORG, PROJECT, 90);

        assertThat(out.calibration().outliers()).isEmpty();
        var band = out.calibration().byComplexity().getFirst();
        assertThat(band.effortHours().p50()).isNull();
        assertThat(band.effortHours().n()).isZero();
        assertThat(band.unitsWithoutEffort()).isEqualTo(2);
    }

    private static FeatureRow feature(ProjectRoadmapV2Output out, String id) {
        return out.features().stream().filter(f -> f.externalId().equals("ext-" + id)).findFirst().orElseThrow();
    }

    private static Instant daysAgo(int days) {
        return NOW.minus(Duration.ofDays(days));
    }

    private static WorkItem node(String id, ResolvedType type, ResolvedStatus status, String parentId) {
        WorkItem item = WorkItemFixture.item(id, type, status, null, parentId == null ? null : "ext-" + parentId);
        item.setLabels(List.of("tema"));
        return item;
    }

    private static WorkItem child(String id, Double effort, ResolvedStatus status, String parentId, Instant completedAt) {
        WorkItem item = WorkItemFixture.item(id, ResolvedType.STORY, status, effort, "ext-" + parentId);
        item.setCompletedAt(completedAt);
        item.setSprintId("s-old");
        item.setExternalUpdatedAt(completedAt != null ? completedAt : daysAgo(5));
        return item;
    }

    private static WorkItem backlog(String id, Double effort, String priority, Instant changedAt, List<String> tags) {
        WorkItem item = WorkItemFixture.item(id, ResolvedType.STORY, ResolvedStatus.TODO, effort, null);
        item.setComplexity(effort);
        item.setPriority(priority);
        item.setExternalUpdatedAt(changedAt);
        item.setLabels(tags);
        return item;
    }

    private static WorkItem parent(WorkItem item, String parentExternalId) {
        item.setParentExternalId(parentExternalId);
        return item;
    }

    private static WorkItem inSprint(WorkItem item, String sprintId) {
        item.setSprintId(sprintId);
        return item;
    }

    private static WorkItem calibrated(String id, Double effort, Double complexity, Double cycle, List<String> tags) {
        WorkItem item = WorkItemFixture.item(id, ResolvedType.STORY, ResolvedStatus.DONE, effort, null);
        item.setComplexity(complexity);
        item.setCycleTimeHours(cycle);
        item.setCompletedAt(Instant.parse("2026-09-12T10:00:00Z"));
        item.setLabels(tags);
        return item;
    }

    private static Sprint sprint(String id, Sprint.TimeFrame timeFrame, String start, String end) {
        return new Sprint(id, ORG, PROJECT, WorkItem.Source.AZURE_DEVOPS, "ext-" + id, "team-1", "Core", "Sprint " + id,
                "\\Orion\\" + id, Instant.parse(start + "T00:00:00Z"), Instant.parse(end + "T00:00:00Z"), timeFrame,
                true, 0L, Instant.parse("2026-01-01T00:00:00Z"), null);
    }
}
