package andromeda.sei.ms.feature.organization.presentation;

import andromeda.sei.ms.feature.organization.application.dto.UpdateAiPricesInput;
import andromeda.sei.ms.feature.organization.application.usecase.GetOrganizationUseCase;
import andromeda.sei.ms.feature.organization.application.usecase.OnboardOrganizationUseCase;
import andromeda.sei.ms.feature.organization.application.usecase.UpdateAiPricesUseCase;
import andromeda.sei.ms.feature.organization.application.usecase.UpdateOrganizationUseCase;
import andromeda.sei.ms.feature.organization.presentation.dto.OnboardOrganizationRequest;
import andromeda.sei.ms.feature.organization.presentation.dto.OnboardOrganizationResponse;
import andromeda.sei.ms.feature.organization.presentation.dto.OrganizationResponse;
import andromeda.sei.ms.feature.organization.presentation.dto.UpdateAiPricesRequest;
import andromeda.sei.ms.feature.organization.presentation.dto.UpdateOrganizationRequest;
import andromeda.sei.ms.shared.infrastructure.security.JwtService;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organizations")
@RequiredArgsConstructor
public class OrganizationController {

    private final OnboardOrganizationUseCase onboardOrganizationUseCase;
    private final GetOrganizationUseCase getOrganizationUseCase;
    private final UpdateOrganizationUseCase updateOrganizationUseCase;
    private final UpdateAiPricesUseCase updateAiPricesUseCase;
    private final OrganizationPresentationMapper mapper;
    private final JwtService jwtService;

    @PostMapping
    public ResponseEntity<OnboardOrganizationResponse> onboard(
            @Valid @RequestBody OnboardOrganizationRequest request) {
        var output = onboardOrganizationUseCase.execute(mapper.toInput(request));
        String token = jwtService.generate(output.userId(), output.orgId(), output.permissionCodes());
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponse(output, token));
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<OrganizationResponse> getMe() {
        String orgId = TenantContextHolder.get();
        var output = getOrganizationUseCase.execute(orgId);
        return ResponseEntity.ok(mapper.toResponse(output));
    }

    @PutMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<OrganizationResponse> updateMe(@Valid @RequestBody UpdateOrganizationRequest request) {
        String orgId = TenantContextHolder.get();
        var output = updateOrganizationUseCase.execute(orgId, request.seniorities(), request.subscriptionCostPerUserUsd());
        return ResponseEntity.ok(mapper.toResponse(output));
    }

    @PutMapping("/ai-prices")
    @PreAuthorize("hasAuthority('PRICE_TABLE_MANAGE')")
    public ResponseEntity<OrganizationResponse> updateAiPrices(@Valid @RequestBody UpdateAiPricesRequest request) {
        String orgId = TenantContextHolder.get();
        var input = new UpdateAiPricesInput(
                request.entries() == null ? null :
                        request.entries().stream()
                                .map(e -> new UpdateAiPricesInput.AiPriceEntryInput(e.model(), e.pricePerKInput(), e.pricePerKOutput()))
                                .toList()
        );
        var output = updateAiPricesUseCase.execute(orgId, input);
        return ResponseEntity.ok(mapper.toResponse(output));
    }
}
