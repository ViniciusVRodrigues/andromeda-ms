package andromeda.sei.ms.feature.organization.presentation;

import andromeda.sei.ms.feature.organization.application.usecase.ConfirmWorkItemPatternsUseCase;
import andromeda.sei.ms.feature.organization.application.usecase.DetectWorkItemPatternsUseCase;
import andromeda.sei.ms.feature.organization.application.usecase.GetWorkItemPatternsUseCase;
import andromeda.sei.ms.feature.organization.application.usecase.PreviewWorkItemPatternsUseCase;
import andromeda.sei.ms.feature.organization.application.usecase.UpdateWorkItemPatternsUseCase;
import andromeda.sei.ms.feature.organization.presentation.dto.WorkItemPatternInput;
import andromeda.sei.ms.feature.organization.presentation.dto.WorkItemPatternPreviewItem;
import andromeda.sei.ms.feature.organization.presentation.dto.WorkItemPatternsPreviewRequest;
import andromeda.sei.ms.feature.organization.presentation.dto.WorkItemPatternsRequest;
import andromeda.sei.ms.feature.organization.presentation.dto.WorkItemPatternsResponse;
import andromeda.sei.ms.shared.infrastructure.security.AndromedaUserDetails;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Organization work item patterns",
        description = "Single branch to work item pattern set, used by the local agent and by the pull request link")
@RestController
@RequestMapping("/api/v1/organizations/me/work-item-patterns")
@RequiredArgsConstructor
public class OrganizationWorkItemPatternsController {

    private final GetWorkItemPatternsUseCase getUseCase;
    private final UpdateWorkItemPatternsUseCase updateUseCase;
    private final ConfirmWorkItemPatternsUseCase confirmUseCase;
    private final PreviewWorkItemPatternsUseCase previewUseCase;
    private final DetectWorkItemPatternsUseCase detectUseCase;

    @Operation(summary = "Get the organization's work item patterns, defaults, presets and last detection")
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<WorkItemPatternsResponse> get() {
        return ResponseEntity.ok(WorkItemPatternsResponse.from(getUseCase.execute(TenantContextHolder.get())));
    }

    @Operation(summary = "Replace the work item patterns (mode CUSTOM; empty restores the defaults)")
    @ApiResponse(responseCode = "403", description = "Missing REPOS_CONFIGURE permission")
    @ApiResponse(responseCode = "422", description = "Invalid pattern (WORK_ITEM_PATTERN_INVALID)")
    @PutMapping
    @PreAuthorize("hasAuthority('REPOS_CONFIGURE')")
    public ResponseEntity<WorkItemPatternsResponse> update(@AuthenticationPrincipal AndromedaUserDetails user,
                                                           @RequestBody WorkItemPatternsRequest request) {
        List<UpdateWorkItemPatternsUseCase.PatternInput> patterns = request.patterns() == null ? null
                : request.patterns().stream()
                .map(p -> p == null ? null : new UpdateWorkItemPatternsUseCase.PatternInput(p.regex(), p.alsoInText()))
                .toList();
        boolean enabled = request.enabled() == null || request.enabled();
        return ResponseEntity.ok(WorkItemPatternsResponse.from(
                updateUseCase.execute(TenantContextHolder.get(), userId(user), enabled, patterns)));
    }

    @Operation(summary = "Confirm the current (typically detected) patterns: mode becomes CUSTOM")
    @ApiResponse(responseCode = "403", description = "Missing REPOS_CONFIGURE permission")
    @PostMapping("/confirm")
    @PreAuthorize("hasAuthority('REPOS_CONFIGURE')")
    public ResponseEntity<WorkItemPatternsResponse> confirm(@AuthenticationPrincipal AndromedaUserDetails user) {
        return ResponseEntity.ok(WorkItemPatternsResponse.from(
                confirmUseCase.execute(TenantContextHolder.get(), userId(user))));
    }

    @Operation(summary = "Preview patterns against sample branches (nothing is stored)")
    @ApiResponse(responseCode = "400", description = "More than 10 patterns or 20 branches")
    @ApiResponse(responseCode = "403", description = "Missing REPOS_CONFIGURE permission")
    @ApiResponse(responseCode = "422", description = "Invalid pattern (WORK_ITEM_PATTERN_INVALID)")
    @PostMapping("/preview")
    @PreAuthorize("hasAuthority('REPOS_CONFIGURE')")
    public ResponseEntity<List<WorkItemPatternPreviewItem>> preview(
            @Valid @RequestBody WorkItemPatternsPreviewRequest request) {
        List<String> regexes = request.patterns() == null ? null
                : request.patterns().stream().map(WorkItemPatternInput::regex).toList();
        return ResponseEntity.ok(previewUseCase.execute(regexes, request.branches()).stream()
                .map(WorkItemPatternPreviewItem::from).toList());
    }

    @Operation(summary = "Run the automatic detection now (not available yet: 501)")
    @ApiResponse(responseCode = "501", description = "WORK_ITEM_PATTERN_DETECTION_UNAVAILABLE")
    @PostMapping("/detect")
    @PreAuthorize("hasAuthority('REPOS_CONFIGURE')")
    public ResponseEntity<WorkItemPatternsResponse> detect() {
        return ResponseEntity.ok(WorkItemPatternsResponse.from(detectUseCase.execute(TenantContextHolder.get())));
    }

    private static String userId(AndromedaUserDetails user) {
        return user == null ? null : user.getUserId();
    }
}
