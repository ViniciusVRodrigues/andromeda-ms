package andromeda.sei.ms.feature.organization.presentation;

import andromeda.sei.ms.shared.infrastructure.web.ClientIpResolver;
import andromeda.sei.ms.feature.organization.application.dto.UpdateAiPricesInput;
import andromeda.sei.ms.feature.organization.application.usecase.DeleteOrganizationUseCase;
import andromeda.sei.ms.feature.organization.application.usecase.GetOrganizationUseCase;
import andromeda.sei.ms.feature.organization.application.usecase.OnboardOrganizationUseCase;
import andromeda.sei.ms.feature.organization.application.usecase.UpdateAiPricesUseCase;
import andromeda.sei.ms.feature.organization.application.usecase.UpdateOrganizationUseCase;
import andromeda.sei.ms.feature.organization.presentation.dto.DeleteOrganizationRequest;
import andromeda.sei.ms.feature.organization.presentation.dto.OnboardOrganizationRequest;
import andromeda.sei.ms.feature.organization.presentation.dto.OnboardOrganizationResponse;
import andromeda.sei.ms.feature.organization.presentation.dto.OrganizationResponse;
import andromeda.sei.ms.feature.organization.presentation.dto.UpdateAiPricesRequest;
import andromeda.sei.ms.feature.organization.presentation.dto.UpdateOrganizationRequest;
import andromeda.sei.ms.shared.infrastructure.ratelimit.RateLimiter;
import andromeda.sei.ms.shared.infrastructure.security.JwtService;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import andromeda.sei.ms.shared.util.SlugUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/organizations")
@RequiredArgsConstructor
public class OrganizationController {

    private final OnboardOrganizationUseCase onboardOrganizationUseCase;
    private final GetOrganizationUseCase getOrganizationUseCase;
    private final UpdateOrganizationUseCase updateOrganizationUseCase;
    private final UpdateAiPricesUseCase updateAiPricesUseCase;
    private final DeleteOrganizationUseCase deleteOrganizationUseCase;
    private final OrganizationPresentationMapper mapper;
    private final JwtService jwtService;
    private final andromeda.sei.ms.feature.organization.domain.OrganizationRepository organizationRepository;
    private final RateLimiter rateLimiter;
    private final ClientIpResolver clientIpResolver;

    @PostMapping
    public ResponseEntity<OnboardOrganizationResponse> onboard(
            @Valid @RequestBody OnboardOrganizationRequest request) {
        var output = onboardOrganizationUseCase.execute(mapper.toInput(request));
        String token = jwtService.generate(output.userId(), output.orgId(), output.permissionCodes());
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponse(output, token));
    }

    @Operation(summary = "Verificar se o nome de organização já está em uso",
            description = "Recebe o NOME e aplica a mesma derivação de slug do cadastro — "
                    + "nomes diferentes podem gerar o mesmo identificador. "
                    + "Responde apenas um booleano, sem nenhum dado da organização detentora.")
    @ApiResponse(responseCode = "200", description = "Disponibilidade retornada")
    @GetMapping("/name-availability")
    public ResponseEntity<Map<String, Object>> nameAvailability(@RequestParam String name,
                                                                HttpServletRequest http) {
        rateLimiter.check("org-availability:" + clientIpResolver.resolve(http), 60, Duration.ofMinutes(5));
        String slug = SlugUtils.toSlug(name == null ? "" : name.trim());
        boolean available = !slug.isBlank() && !organizationRepository.existsBySlug(slug);
        return ResponseEntity.ok(Map.of("slug", slug, "available", available));
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

    @Operation(summary = "Excluir a organização do token (exclusão lógica)",
            description = "Exige ORGANIZATION_DELETE. O corpo traz o slug da organização como confirmação; "
                    + "depois da exclusão ninguém da organização entra nem usa token já emitido.")
    @ApiResponse(responseCode = "204", description = "Organização excluída")
    @ApiResponse(responseCode = "403", description = "Sem a permissão ORGANIZATION_DELETE")
    @ApiResponse(responseCode = "404", description = "Organização não encontrada")
    @ApiResponse(responseCode = "422", description = "Confirmação diferente do slug (ORGANIZATION_DELETE_CONFIRMATION_MISMATCH)")
    @DeleteMapping("/me")
    @PreAuthorize("hasAuthority('ORGANIZATION_DELETE')")
    public ResponseEntity<Void> deleteMe(@Valid @RequestBody DeleteOrganizationRequest request) {
        deleteOrganizationUseCase.execute(TenantContextHolder.get(), request.confirmSlug());
        return ResponseEntity.noContent().build();
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
