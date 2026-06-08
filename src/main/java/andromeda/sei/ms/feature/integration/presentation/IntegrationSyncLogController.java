package andromeda.sei.ms.feature.integration.presentation;

import andromeda.sei.ms.feature.integration.application.dto.SyncLogOutput;
import andromeda.sei.ms.feature.integration.application.usecase.GetIntegrationSyncLogsUseCase;
import andromeda.sei.ms.feature.integration.domain.Integration;
import andromeda.sei.ms.feature.integration.presentation.dto.SyncLogResponse;
import andromeda.sei.ms.shared.domain.exception.ForbiddenOperationException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Integration Sync Logs", description = "Query sync logs for all integrations")
@RestController
@RequestMapping("/api/v1/integrations/sync-logs")
@RequiredArgsConstructor
public class IntegrationSyncLogController {

    private final GetIntegrationSyncLogsUseCase getIntegrationSyncLogsUseCase;

    @Operation(summary = "Get integration sync logs")
    @ApiResponse(responseCode = "200", description = "Sync logs returned")
    @ApiResponse(responseCode = "403", description = "No permission for this integration")
    @GetMapping
    @PreAuthorize("hasAnyAuthority('AZURE_READ', 'GITHUB_READ')")
    public ResponseEntity<List<SyncLogResponse>> getSyncLogs(
            @RequestParam Integration integration,
            @RequestParam(required = false) String projectId) {

        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            boolean hasAzureRead = auth.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("AZURE_READ"));
            boolean hasGithubRead = auth.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("GITHUB_READ"));

            if (integration == Integration.AZURE_DEVOPS && !hasAzureRead) {
                throw new ForbiddenOperationException("SYNC_LOG_FORBIDDEN",
                        "AZURE_READ permission required for Azure DevOps sync logs");
            }
            if (integration == Integration.GITHUB && !hasGithubRead) {
                throw new ForbiddenOperationException("SYNC_LOG_FORBIDDEN",
                        "GITHUB_READ permission required for GitHub sync logs");
            }
        }

        List<SyncLogOutput> logs = getIntegrationSyncLogsUseCase.execute(integration, projectId);
        List<SyncLogResponse> response = logs.stream().map(l -> new SyncLogResponse(
                l.id(), l.integration(), l.operation(), l.status(), l.triggeredBy(),
                l.projectId(), l.startedAt(), l.finishedAt(), l.durationMs(),
                l.itemsProcessed(), l.itemsFailed(), l.errorCode(), l.errorMessage()
        )).toList();

        return ResponseEntity.ok(response);
    }
}
