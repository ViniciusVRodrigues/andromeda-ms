package andromeda.sei.ms.feature.organization.presentation;

import andromeda.sei.ms.feature.organization.application.usecase.GetAllocationPolicyUseCase;
import andromeda.sei.ms.feature.organization.application.usecase.UpdateAllocationPolicyUseCase;
import andromeda.sei.ms.feature.organization.presentation.dto.AllocationPolicyRequest;
import andromeda.sei.ms.feature.organization.presentation.dto.AllocationPolicyResponse;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Organization allocation policy", description = "Cap of AI usage that may go to Fora de entrega")
@RestController
@RequestMapping("/api/v1/organizations/me/allocation-policy")
@RequiredArgsConstructor
public class OrganizationAllocationPolicyController {

    private final GetAllocationPolicyUseCase getAllocationPolicyUseCase;
    private final UpdateAllocationPolicyUseCase updateAllocationPolicyUseCase;

    @Operation(summary = "Get the allocation policy of the organization")
    @ApiResponse(responseCode = "200", description = "Allocation policy (default 15 when never configured)")
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AllocationPolicyResponse> get() {
        return ResponseEntity.ok(AllocationPolicyResponse.from(
                getAllocationPolicyUseCase.execute(TenantContextHolder.get())));
    }

    @Operation(summary = "Update the allocation policy of the organization")
    @ApiResponse(responseCode = "200", description = "Allocation policy updated")
    @ApiResponse(responseCode = "400", description = "Cap missing or outside 0..100 (VALIDATION_ERROR)")
    @ApiResponse(responseCode = "403", description = "Missing USER_MANAGE permission")
    @PutMapping
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public ResponseEntity<AllocationPolicyResponse> update(@Valid @RequestBody AllocationPolicyRequest request) {
        return ResponseEntity.ok(AllocationPolicyResponse.from(
                updateAllocationPolicyUseCase.execute(TenantContextHolder.get(), request.outOfDeliveryCapPercent())));
    }
}
