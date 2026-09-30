package andromeda.sei.ms.feature.workitem.presentation;

import andromeda.sei.ms.feature.workitem.application.dto.SprintMetricsOutput;
import andromeda.sei.ms.feature.workitem.application.usecase.GetSprintMetricsUseCase;
import andromeda.sei.ms.feature.workitem.domain.Sprint;
import andromeda.sei.ms.shared.presentation.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AzureMetricsControllerTest {

    @Mock
    private GetSprintMetricsUseCase getSprintMetricsUseCase;

    @InjectMocks
    private AzureMetricsController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void returns200WithSprintList() throws Exception {
        SprintMetricsOutput output = new SprintMetricsOutput(
                "sprint-1", "Sprint 1", Instant.parse("2025-01-01T00:00:00Z"),
                Instant.parse("2025-01-14T00:00:00Z"), Sprint.TimeFrame.PAST,
                16.0, 15.0, 20.0, 18.0, 15.0, 20.0, 18.0, 25.0, 3, 5, 3, Instant.parse("2025-01-15T00:00:00Z")
        );

        when(getSprintMetricsUseCase.execute(eq("proj-1"))).thenReturn(List.of(output));

        mockMvc.perform(get("/api/v1/metrics/azure/sprints").param("projectId", "proj-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sprintId").value("sprint-1"))
                .andExpect(jsonPath("$[0].velocity").value(16.0))
                .andExpect(jsonPath("$[0].completedItemCount").value(3))
                .andExpect(jsonPath("$[0].plannedEffort").value(18.0))
                .andExpect(jsonPath("$[0].cycleTimeP50").value(15.0))
                .andExpect(jsonPath("$[0].leadTimeP90").value(25.0));
    }

    @Test
    void returnsErrorWhenProjectIdMissing() throws Exception {
        mockMvc.perform(get("/api/v1/metrics/azure/sprints"))
                .andExpect(status().is5xxServerError());
    }

    @Test
    void returns200WithEmptyListWhenNoMetrics() throws Exception {
        when(getSprintMetricsUseCase.execute(eq("proj-empty"))).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/metrics/azure/sprints").param("projectId", "proj-empty"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }
}
