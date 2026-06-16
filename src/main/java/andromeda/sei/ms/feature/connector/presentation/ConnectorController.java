package andromeda.sei.ms.feature.connector.presentation;

import andromeda.sei.ms.feature.aiusage.application.dto.AIUsageEntry;
import andromeda.sei.ms.feature.aiusage.application.usecase.UpsertAIUsageUseCase;
import andromeda.sei.ms.feature.connector.application.usecase.StoreFluencyScoreUseCase;
import andromeda.sei.ms.feature.connector.presentation.dto.AiefUploadRequest;
import andromeda.sei.ms.feature.connector.presentation.dto.FluencyScoreRequest;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Tag(name = "Connector", description = "AIEF Sharing Server contract endpoints")
@RestController
@RequestMapping("/connector/{orgSlug}")
@RequiredArgsConstructor
@Slf4j
public class ConnectorController {

    private final UpsertAIUsageUseCase upsertAIUsageUseCase;
    private final StoreFluencyScoreUseCase storeFluencyScoreUseCase;

    @Operation(summary = "Health check — public, no authentication required")
    @ApiResponse(responseCode = "200", description = "Service is healthy")
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health(@PathVariable String orgSlug) {
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "timestamp", Instant.now().toString()
        ));
    }

    @Operation(summary = "Upload AI usage data (AIEF contract)")
    @ApiResponse(responseCode = "200", description = "Usage data accepted")
    @ApiResponse(responseCode = "401", description = "Invalid or revoked connector token")
    @PostMapping("/{rawToken}/api/upload")
    public ResponseEntity<Map<String, Integer>> upload(
            @PathVariable String orgSlug,
            @PathVariable String rawToken,
            @Valid @RequestBody List<AiefUploadRequest> entries) {

        log.info("connector upload: org={} token={}... entries={} body={}",
                orgSlug, rawToken.substring(0, Math.min(8, rawToken.length())), entries.size(), entries);

        String organizationId = TenantContextHolder.get();
        String userId = resolveUserId();

        List<AIUsageEntry> usageEntries = entries.stream()
                .map(e -> new AIUsageEntry(
                        e.datasetId() != null ? e.datasetId() : "default",
                        e.gitHubLogin(),
                        e.day(),
                        e.model(),
                        e.workspaceId(),
                        e.workspaceName(),
                        e.machineId(),
                        e.machineName(),
                        e.inputTokens(),
                        e.outputTokens(),
                        e.interactions(),
                        e.editor()
                ))
                .toList();

        upsertAIUsageUseCase.execute(usageEntries, organizationId, userId);
        return ResponseEntity.ok(Map.of("uploaded", usageEntries.size()));
    }

    @Operation(summary = "Store pre-computed fluency score (AIEF contract)")
    @ApiResponse(responseCode = "200", description = "Fluency score stored")
    @ApiResponse(responseCode = "401", description = "Invalid or revoked connector token")
    @PostMapping("/{rawToken}/api/fluency-score")
    public ResponseEntity<Map<String, Boolean>> fluencyScore(
            @PathVariable String orgSlug,
            @PathVariable String rawToken,
            @Valid @RequestBody FluencyScoreRequest body) {

        log.info("connector fluency-score: org={} token={}... body={}",
                orgSlug, rawToken.substring(0, Math.min(8, rawToken.length())), body);

        String organizationId = TenantContextHolder.get();
        String userId = resolveUserId();

        storeFluencyScoreUseCase.execute(body, organizationId, userId);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @Operation(summary = "Retrieve AI usage data for the authenticated token owner")
    @ApiResponse(responseCode = "200", description = "Usage data")
    @ApiResponse(responseCode = "401", description = "Invalid or revoked connector token")
    @GetMapping("/{rawToken}/api/data")
    public ResponseEntity<Map<String, String>> data(
            @PathVariable String orgSlug,
            @PathVariable String rawToken) {
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    private String resolveUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof String userId) {
            return userId;
        }
        return null;
    }
}
