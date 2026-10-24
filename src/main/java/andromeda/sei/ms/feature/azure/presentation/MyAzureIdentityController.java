package andromeda.sei.ms.feature.azure.presentation;

import andromeda.sei.ms.feature.azure.application.dto.MyAzureIdentityOutput;
import andromeda.sei.ms.feature.azure.application.usecase.GetMyAzureIdentityUseCase;
import andromeda.sei.ms.feature.azure.presentation.dto.MyAzureIdentityResponse;
import andromeda.sei.ms.shared.infrastructure.security.AndromedaUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Self-service: subject and tenant come only from the authenticated principal. No permission code
 * beyond authentication, like {@code GET /api/v1/auth/me}.
 */
@Tag(name = "Azure DevOps", description = "Caller's own Azure DevOps identity link state")
@RestController
@RequestMapping("/api/v1/users/me/azure-identity")
@RequiredArgsConstructor
public class MyAzureIdentityController {

    private final GetMyAzureIdentityUseCase getMyAzureIdentityUseCase;

    @Operation(summary = "State of the caller's own Azure identity link (read-only)")
    @ApiResponse(responseCode = "200", description = "LINKED, NOT_FOUND, AMBIGUOUS, TAKEN or NO_CONNECTION")
    @GetMapping
    public ResponseEntity<MyAzureIdentityResponse> get(@AuthenticationPrincipal AndromedaUserDetails principal) {
        MyAzureIdentityOutput out = getMyAzureIdentityUseCase.execute(principal.getOrganizationId(), principal.getUserId());
        return ResponseEntity.ok(new MyAzureIdentityResponse(out.status(), out.azureEmail(), out.displayName(),
                out.source(), out.linkedAt()));
    }
}
