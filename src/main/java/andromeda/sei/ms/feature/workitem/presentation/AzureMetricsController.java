package andromeda.sei.ms.feature.workitem.presentation;

import andromeda.sei.ms.feature.workitem.application.dto.SprintMetricsOutput;
import andromeda.sei.ms.feature.workitem.application.usecase.GetSprintMetricsUseCase;
import andromeda.sei.ms.feature.workitem.presentation.dto.SprintMetricsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Azure Metrics", description = "Pre-calculated Azure DevOps agile metrics")
@RestController
@RequestMapping("/api/v1/metrics/azure")
@RequiredArgsConstructor
public class AzureMetricsController {

    private final GetSprintMetricsUseCase getSprintMetricsUseCase;

    @Operation(
            summary = "Get sprint metrics for a project",
            description = "Returns pre-calculated velocity, cycle time, and lead time metrics per sprint. " +
                    "Results are sorted by sprint start date descending. Requires AZURE_READ permission."
    )
    @Parameter(name = "projectId", description = "Internal project ID", required = true)
    @ApiResponse(responseCode = "200", description = "Sprint metrics list (may be empty)")
    @ApiResponse(responseCode = "400", description = "projectId query parameter is missing")
    @ApiResponse(responseCode = "403", description = "Caller lacks AZURE_READ permission")
    @GetMapping("/sprints")
    @PreAuthorize("@perm.onProject(#projectId, 'AZURE_READ')")
    public ResponseEntity<List<SprintMetricsResponse>> getSprintMetrics(
            @RequestParam(required = true) String projectId) {

        List<SprintMetricsOutput> outputs = getSprintMetricsUseCase.execute(projectId);

        List<SprintMetricsResponse> response = outputs.stream()
                .map(o -> new SprintMetricsResponse(
                        o.sprintId(),
                        o.sprintName(),
                        o.startDate(),
                        o.endDate(),
                        o.timeFrame(),
                        o.velocity(),
                        o.avgCycleTime(),
                        o.avgLeadTime(),
                        o.plannedEffort(),
                        o.cycleTimeP50(),
                        o.cycleTimeP90(),
                        o.leadTimeP50(),
                        o.leadTimeP90(),
                        o.completedItemCount(),
                        o.totalItemCount(),
                        o.itemsWithPoints(),
                        o.calculatedAt()
                ))
                .toList();

        return ResponseEntity.ok(response);
    }
}
