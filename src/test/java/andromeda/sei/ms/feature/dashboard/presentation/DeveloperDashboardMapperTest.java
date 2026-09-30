package andromeda.sei.ms.feature.dashboard.presentation;

import andromeda.sei.ms.feature.dashboard.application.dto.DeveloperDashboardOutput;
import andromeda.sei.ms.feature.dashboard.application.dto.SprintHistoryPoint;
import andromeda.sei.ms.feature.dashboard.presentation.dto.DeveloperDashboardResponse;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * dashboard-rework-and-pricing 1.5: the sprint-items summary must carry type and
 * externalId (card identification), and sprintHistory must map through 1:1.
 */
class DeveloperDashboardMapperTest {

    private final DeveloperDashboardMapper mapper = new DeveloperDashboardMapper();

    @Test
    void should_map_type_and_externalId_into_workItemSummary() {
        WorkItem wi = mock(WorkItem.class);
        when(wi.getId()).thenReturn("wi-1");
        when(wi.getTitle()).thenReturn("Corrigir rate limit");
        when(wi.getResolvedStatus()).thenReturn(WorkItem.ResolvedStatus.IN_PROGRESS);
        when(wi.getResolvedType()).thenReturn(WorkItem.ResolvedType.BUG);
        when(wi.getExternalId()).thenReturn("AZDO-417");

        DeveloperDashboardOutput output = outputWithItems(List.of(wi), List.of());

        DeveloperDashboardResponse response = mapper.toResponse(output);

        assertThat(response.currentSprintItems()).hasSize(1);
        assertThat(response.currentSprintItems().get(0).type()).isEqualTo(WorkItem.ResolvedType.BUG);
        assertThat(response.currentSprintItems().get(0).externalId()).isEqualTo("AZDO-417");
    }

    @Test
    void should_map_sprintHistory_points() {
        SprintHistoryPoint point = new SprintHistoryPoint(
                "s-1", "Sprint 21", LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 10),
                21.0, 18.0, 4.5, 12000L, new BigDecimal("0.42"), 3.5);

        DeveloperDashboardResponse response = mapper.toResponse(outputWithItems(List.of(), List.of(point)));

        assertThat(response.sprintHistory()).hasSize(1);
        DeveloperDashboardResponse.SprintHistoryResponse mapped = response.sprintHistory().get(0);
        assertThat(mapped.sprintId()).isEqualTo("s-1");
        assertThat(mapped.name()).isEqualTo("Sprint 21");
        assertThat(mapped.spPlanned()).isEqualTo(21.0);
        assertThat(mapped.spDelivered()).isEqualTo(18.0);
        assertThat(mapped.totalTokens()).isEqualTo(12000L);
        assertThat(mapped.totalCostUsd()).isEqualByComparingTo("0.42");
        assertThat(mapped.cycleTimeP50()).isEqualTo(3.5);
        assertThat(response.cycleTime().n()).isZero();
        assertThat(response.leadTime().p50()).isNull();
    }

    @Test
    void should_map_null_sprintHistory_to_empty_list() {
        DeveloperDashboardOutput output = outputWithItems(List.of(), null);

        DeveloperDashboardResponse response = mapper.toResponse(output);

        assertThat(response.sprintHistory()).isEmpty();
    }

    private DeveloperDashboardOutput outputWithItems(List<WorkItem> items,
                                                      List<SprintHistoryPoint> history) {
        return new DeveloperDashboardOutput(
                items, null, null, null, null, List.of(), BigDecimal.ZERO, null, null, null,
                List.of(), items.size(), List.of(), null, null, null, history);
    }
}
