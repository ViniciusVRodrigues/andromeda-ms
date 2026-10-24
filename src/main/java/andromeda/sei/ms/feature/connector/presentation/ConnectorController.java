package andromeda.sei.ms.feature.connector.presentation;

import andromeda.sei.ms.feature.aiusage.application.dto.AIUsageEntry;
import andromeda.sei.ms.feature.aiusage.application.usecase.UpsertAIUsageUseCase;
import andromeda.sei.ms.feature.connector.presentation.dto.AiefUploadRequest;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import tools.jackson.databind.json.JsonMapper;
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

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Tag(name = "Connector", description = "AIEF Sharing Server contract endpoints")
@RestController
@RequestMapping("/connector/{orgSlug}")
@RequiredArgsConstructor
@Slf4j
public class ConnectorController {

    /** Fluency sink body cap (100 KB): the body is read bounded, never trusted by Content-Length. */
    static final int FLUENCY_SINK_MAX_BYTES = 100 * 1024;
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final UpsertAIUsageUseCase upsertAIUsageUseCase;

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

        long startedAt = System.nanoTime();

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
        log.info("connector upload: org={} entries={} ms={}", orgSlug, usageEntries.size(), elapsedMs(startedAt));
        return ResponseEntity.ok(Map.of("uploaded", usageEntries.size()));
    }

    @Operation(summary = "Fluency score sink (feature removed; body is validated as JSON, size-capped and discarded)")
    @ApiResponse(responseCode = "200", description = "Accepted and discarded")
    @ApiResponse(responseCode = "400", description = "Body is not JSON or exceeds 100 KB")
    @ApiResponse(responseCode = "401", description = "Invalid or revoked connector token")
    @PostMapping("/{rawToken}/api/fluency-score")
    public ResponseEntity<Map<String, Object>> fluencyScore(
            @PathVariable String orgSlug,
            @PathVariable String rawToken,
            HttpServletRequest request) throws IOException {
        byte[] raw = request.getInputStream().readNBytes(FLUENCY_SINK_MAX_BYTES + 1);
        if (raw.length > FLUENCY_SINK_MAX_BYTES) {
            return ResponseEntity.badRequest().body(Map.of("code", "PAYLOAD_TOO_LARGE"));
        }
        try {
            JSON.readTree(raw);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("code", "INVALID_JSON"));
        }
        log.info("connector fluency-score discarded: org={}", orgSlug);
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

    private static long elapsedMs(long startedAtNanos) {
        return (System.nanoTime() - startedAtNanos) / 1_000_000;
    }

    private String resolveUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof String userId) {
            return userId;
        }
        return null;
    }
}
