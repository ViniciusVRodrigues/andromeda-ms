package andromeda.sei.ms.feature.organization.presentation;

import andromeda.sei.ms.feature.organization.application.usecase.GetAgentSettingsUseCase;
import andromeda.sei.ms.feature.organization.application.usecase.PreviewAgentBranchPatternsUseCase;
import andromeda.sei.ms.feature.organization.presentation.dto.AgentBranchPreviewItem;
import andromeda.sei.ms.feature.organization.presentation.dto.AgentBranchPreviewRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import java.util.List;
import andromeda.sei.ms.feature.organization.application.usecase.UpdateAgentSettingsUseCase;
import andromeda.sei.ms.feature.organization.presentation.dto.AgentSettingsRequest;
import andromeda.sei.ms.feature.organization.presentation.dto.AgentSettingsResponse;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Organization agent settings", description = "Branch patterns used to attribute local-agent usage to work items")
@RestController
@RequestMapping("/api/v1/organizations/me/agent-settings")
@RequiredArgsConstructor
public class OrganizationAgentSettingsController {

    private final GetAgentSettingsUseCase getAgentSettingsUseCase;
    private final UpdateAgentSettingsUseCase updateAgentSettingsUseCase;
    private final PreviewAgentBranchPatternsUseCase previewUseCase;

    @Operation(summary = "Get the organization's agent branch patterns")
    @ApiResponse(responseCode = "200", description = "Patterns, defaults and whether the defaults are in use")
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AgentSettingsResponse> get() {
        return ResponseEntity.ok(AgentSettingsResponse.from(
                getAgentSettingsUseCase.execute(TenantContextHolder.get())));
    }

    @Operation(summary = "Replace the organization's agent branch patterns (empty restores the defaults)")
    @ApiResponse(responseCode = "200", description = "Patterns updated")
    @ApiResponse(responseCode = "403", description = "Missing REPOS_CONFIGURE permission")
    @ApiResponse(responseCode = "422", description = "Invalid pattern (AGENT_BRANCH_PATTERN_INVALID)")
    @PutMapping
    @PreAuthorize("hasAuthority('REPOS_CONFIGURE')")
    public ResponseEntity<AgentSettingsResponse> update(@RequestBody AgentSettingsRequest request) {
        return ResponseEntity.ok(AgentSettingsResponse.from(
                updateAgentSettingsUseCase.execute(TenantContextHolder.get(), request.branchPatterns())));
    }

    @Operation(summary = "Preview patterns against sample branches (nothing is stored)")
    @ApiResponse(responseCode = "200", description = "One entry per branch: reference, pattern index and match span")
    @ApiResponse(responseCode = "400", description = "More than 10 patterns or 20 branches")
    @ApiResponse(responseCode = "403", description = "Missing REPOS_CONFIGURE permission")
    @ApiResponse(responseCode = "422", description = "Invalid pattern (AGENT_BRANCH_PATTERN_INVALID)")
    @PostMapping("/preview")
    @PreAuthorize("hasAuthority('REPOS_CONFIGURE')")
    public ResponseEntity<List<AgentBranchPreviewItem>> preview(@Valid @RequestBody AgentBranchPreviewRequest request) {
        return ResponseEntity.ok(previewUseCase.execute(request.branchPatterns(), request.branches()).stream()
                .map(AgentBranchPreviewItem::from).toList());
    }
}
