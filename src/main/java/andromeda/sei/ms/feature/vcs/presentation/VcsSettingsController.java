package andromeda.sei.ms.feature.vcs.presentation;

import andromeda.sei.ms.feature.vcs.application.dto.WorkItemLinkSettingsInput;
import andromeda.sei.ms.feature.vcs.application.dto.WorkItemLinkSettingsOutput;
import andromeda.sei.ms.feature.vcs.application.usecase.GetWorkItemLinkSettingsUseCase;
import andromeda.sei.ms.feature.vcs.application.usecase.UpdateWorkItemLinkSettingsUseCase;
import andromeda.sei.ms.feature.vcs.presentation.dto.WorkItemLinkSettingsRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Organization-wide VCS settings (vcs-work-item-linking). */
@Tag(name = "Repository settings")
@RestController
@RequestMapping("/api/v1/repos/settings")
@RequiredArgsConstructor
public class VcsSettingsController {

    private final GetWorkItemLinkSettingsUseCase getWorkItemLinkSettingsUseCase;
    private final UpdateWorkItemLinkSettingsUseCase updateWorkItemLinkSettingsUseCase;

    @Operation(summary = "How pull requests reference work items (enabled flag and prefixes, default AB#)")
    @GetMapping("/work-item-link")
    // Organization-level setting/status: open to whoever reads repository data somewhere.
    @PreAuthorize("@perm.onAnyProject('REPOS_READ')")
    public ResponseEntity<WorkItemLinkSettingsOutput> getWorkItemLink() {
        return ResponseEntity.ok(getWorkItemLinkSettingsUseCase.execute());
    }

    @Operation(summary = "Update how pull requests reference work items, for every provider")
    @PutMapping("/work-item-link")
    @PreAuthorize("hasAuthority('REPOS_CONFIGURE')")
    public ResponseEntity<WorkItemLinkSettingsOutput> updateWorkItemLink(@RequestBody WorkItemLinkSettingsRequest request) {
        return ResponseEntity.ok(updateWorkItemLinkSettingsUseCase.execute(
                new WorkItemLinkSettingsInput(request.enabled(), request.prefixes())));
    }
}
