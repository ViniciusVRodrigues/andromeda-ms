package andromeda.sei.ms.feature.workitem.application.usecase;

import andromeda.sei.ms.feature.workitem.application.dto.SprintMetricsOutput;
import andromeda.sei.ms.feature.workitem.domain.Sprint;
import andromeda.sei.ms.feature.workitem.domain.SprintMetrics;
import andromeda.sei.ms.feature.workitem.domain.SprintMetricsRepository;
import andromeda.sei.ms.feature.workitem.domain.SprintRepository;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * metrics-correctness 1.3 — {@code sprint_metrics} documents computed for a non-canonical twin of a
 * shared iteration (before the deduplication) are hidden, so each iteration is listed once.
 */
@ExtendWith(MockitoExtension.class)
class GetSprintMetricsUseCaseTest {

    private static final String ORG = "org-1";
    private static final String PROJECT = "proj-1";
    private static final Instant T0 = Instant.parse("2026-06-01T00:00:00Z");

    @Mock private SprintMetricsRepository sprintMetricsRepository;
    @Mock private SprintRepository sprintRepository;

    @InjectMocks private GetSprintMetricsUseCase useCase;

    @BeforeEach
    void setUp() {
        TenantContextHolder.set(ORG);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void should_hideStaleMetricsOfNonCanonicalTwin() {
        Sprint teamA = sprint("s-a", "team-a", T0);
        Sprint teamB = sprint("s-b", "team-b", T0.plusSeconds(60));
        when(sprintRepository.findAllByOrganizationIdAndProjectId(ORG, PROJECT)).thenReturn(List.of(teamB, teamA));
        when(sprintMetricsRepository.findAllByOrganizationIdAndProjectId(ORG, PROJECT))
                .thenReturn(List.of(metrics("s-a", 8.0), metrics("s-b", 0.0)));

        List<SprintMetricsOutput> output = useCase.execute(PROJECT);

        assertThat(output).extracting(SprintMetricsOutput::sprintId).containsExactly("s-a");
    }

    private static Sprint sprint(String id, String teamId, Instant createdAt) {
        return new Sprint(id, ORG, PROJECT, WorkItem.Source.AZURE_DEVOPS, "iter-25", teamId, teamId, "Sprint 25",
                "Proj\\Sprint 25", T0, T0.plusSeconds(1_209_600), Sprint.TimeFrame.PAST, true, 0L, createdAt, createdAt);
    }

    private static SprintMetrics metrics(String sprintId, double velocity) {
        return new SprintMetrics(null, ORG, PROJECT, sprintId, WorkItem.Source.AZURE_DEVOPS, velocity,
                null, null, 0, 0, 0, T0, true, null, null, null);
    }
}
