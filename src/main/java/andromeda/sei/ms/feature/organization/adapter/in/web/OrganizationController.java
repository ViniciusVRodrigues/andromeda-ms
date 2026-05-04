package andromeda.sei.ms.feature.organization.adapter.in.web;

import andromeda.sei.ms.feature.organization.adapter.in.web.dto.CreateOrganizationRequest;
import andromeda.sei.ms.feature.organization.adapter.in.web.dto.CreateOrganizationResponse;
import andromeda.sei.ms.feature.organization.application.port.in.CreateOrganizationUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/organizations")
@RequiredArgsConstructor
@Tag(name = "Organizations", description = "Organization onboarding and management")
public class OrganizationController {

    private final CreateOrganizationUseCase createOrganizationUseCase;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create organization", description = "Creates a new organization and its first admin user. Returns a JWT access token.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Organization created"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "422", description = "Organization slug already exists")
    })
    public CreateOrganizationResponse create(@Valid @RequestBody CreateOrganizationRequest request) {
        return createOrganizationUseCase.execute(request);
    }
}
