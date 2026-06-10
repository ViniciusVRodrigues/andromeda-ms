package andromeda.sei.ms.feature.connector.presentation;

import andromeda.sei.ms.feature.connector.application.dto.ConnectorTokenInput;
import andromeda.sei.ms.feature.connector.application.dto.ConnectorTokenOutput;
import andromeda.sei.ms.feature.connector.application.usecase.GenerateConnectorTokenUseCase;
import andromeda.sei.ms.feature.connector.application.usecase.ListConnectorTokensUseCase;
import andromeda.sei.ms.feature.connector.application.usecase.RevokeConnectorTokenUseCase;
import andromeda.sei.ms.feature.connector.presentation.dto.ConnectorTokenResponse;
import andromeda.sei.ms.shared.infrastructure.security.AndromedaUserDetails;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Connector Tokens", description = "Connector token management for AIEF integration")
@RestController
@RequestMapping("/api/v1/connector-tokens")
@RequiredArgsConstructor
public class ConnectorTokenController {

    private final GenerateConnectorTokenUseCase generateConnectorTokenUseCase;
    private final ListConnectorTokensUseCase listConnectorTokensUseCase;
    private final RevokeConnectorTokenUseCase revokeConnectorTokenUseCase;

    @Operation(summary = "Generate a new connector token")
    @ApiResponse(responseCode = "201", description = "Token generated — rawToken only visible here")
    @ApiResponse(responseCode = "403", description = "No permission")
    @PostMapping
    @PreAuthorize("hasAuthority('CONNECTOR_TOKEN_MANAGE_OWN')")
    public ResponseEntity<ConnectorTokenResponse> generate(
            @AuthenticationPrincipal AndromedaUserDetails userDetails) {
        String organizationId = TenantContextHolder.get();
        ConnectorTokenInput input = new ConnectorTokenInput(organizationId, userDetails.getUserId());
        ConnectorTokenOutput output = generateConnectorTokenUseCase.execute(input);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ConnectorTokenResponse.from(output));
    }

    @Operation(summary = "List active connector tokens for the authenticated user")
    @ApiResponse(responseCode = "200", description = "Token list")
    @ApiResponse(responseCode = "403", description = "No permission")
    @GetMapping
    @PreAuthorize("hasAuthority('CONNECTOR_TOKEN_MANAGE_OWN')")
    public ResponseEntity<List<ConnectorTokenResponse>> list(
            @AuthenticationPrincipal AndromedaUserDetails userDetails) {
        String organizationId = TenantContextHolder.get();
        List<ConnectorTokenOutput> outputs = listConnectorTokensUseCase.execute(
                organizationId, userDetails.getUserId());
        return ResponseEntity.ok(outputs.stream().map(ConnectorTokenResponse::from).toList());
    }

    @Operation(summary = "Revoke a connector token")
    @ApiResponse(responseCode = "204", description = "Token revoked")
    @ApiResponse(responseCode = "404", description = "Token not found")
    @ApiResponse(responseCode = "403", description = "No permission")
    @DeleteMapping("/{tokenId}")
    @PreAuthorize("hasAuthority('CONNECTOR_TOKEN_MANAGE_OWN')")
    public ResponseEntity<Void> revoke(
            @PathVariable String tokenId,
            @AuthenticationPrincipal AndromedaUserDetails userDetails) {
        String organizationId = TenantContextHolder.get();
        revokeConnectorTokenUseCase.execute(tokenId, organizationId);
        return ResponseEntity.noContent().build();
    }
}
