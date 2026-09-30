package andromeda.sei.ms.feature.vcs.presentation;

import andromeda.sei.ms.feature.vcs.application.usecase.GetServiceIdentitiesUseCase;
import andromeda.sei.ms.feature.vcs.application.usecase.UpdateServiceIdentitiesUseCase;
import andromeda.sei.ms.feature.vcs.presentation.dto.ServiceIdentitiesRequest;
import andromeda.sei.ms.feature.vcs.presentation.dto.ServiceIdentitiesResponse;
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

/** The organization's service identity list, treated as bots in review metrics (azure-repos-provider D5). */
@Tag(name = "Repository settings")
@RestController
@RequestMapping("/api/v1/repos/settings/service-identities")
@RequiredArgsConstructor
public class VcsServiceIdentitiesController {

    private final GetServiceIdentitiesUseCase getServiceIdentitiesUseCase;
    private final UpdateServiceIdentitiesUseCase updateServiceIdentitiesUseCase;

    @Operation(summary = "Provider identities flagged as service accounts (bots) by the organization")
    @ApiResponse(responseCode = "200", description = "Current list")
    @ApiResponse(responseCode = "403", description = "No permission")
    @GetMapping
    // Organization-level setting/status: open to whoever reads repository data somewhere.
    @PreAuthorize("@perm.onAnyProject('REPOS_READ')")
    public ResponseEntity<ServiceIdentitiesResponse> get() {
        return ResponseEntity.ok(new ServiceIdentitiesResponse(getServiceIdentitiesUseCase.execute()));
    }

    @Operation(summary = "Replace the service identity list (ids, unique names or display names)")
    @ApiResponse(responseCode = "200", description = "Stored list")
    @ApiResponse(responseCode = "403", description = "No permission")
    @PutMapping
    @PreAuthorize("hasAuthority('REPOS_CONFIGURE')")
    public ResponseEntity<ServiceIdentitiesResponse> update(@RequestBody ServiceIdentitiesRequest request) {
        return ResponseEntity.ok(new ServiceIdentitiesResponse(updateServiceIdentitiesUseCase.execute(request.identities())));
    }
}
