package andromeda.sei.ms.feature.provideraccount.presentation;

import andromeda.sei.ms.feature.provideraccount.application.usecase.ListMyProviderAccountsUseCase;
import andromeda.sei.ms.feature.provideraccount.application.usecase.UnlinkProviderAccountUseCase;
import andromeda.sei.ms.feature.provideraccount.domain.Provider;
import andromeda.sei.ms.feature.provideraccount.domain.exception.ProviderInvalidException;
import andromeda.sei.ms.feature.provideraccount.presentation.dto.ProviderAccountResponse;
import andromeda.sei.ms.shared.infrastructure.security.AndromedaUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Self-service endpoints scoped strictly to the caller's own account — userId/organizationId are
 * taken only from the authenticated principal, never from the request (design.md D5). No
 * permission code is required beyond authentication, mirroring {@code GET /api/v1/auth/me}.
 */
@Tag(name = "Provider Accounts", description = "Self-service linking of the caller's own external provider accounts")
@RestController
@RequestMapping("/api/v1/users/me/provider-accounts")
@RequiredArgsConstructor
public class UserProviderAccountController {

    private final ListMyProviderAccountsUseCase listMyProviderAccountsUseCase;
    private final UnlinkProviderAccountUseCase unlinkProviderAccountUseCase;
    private final UserProviderAccountPresentationMapper mapper;

    @Operation(summary = "List the caller's own linked provider accounts")
    @ApiResponse(responseCode = "200", description = "Accounts listed")
    @GetMapping
    public ResponseEntity<List<ProviderAccountResponse>> list(@AuthenticationPrincipal AndromedaUserDetails principal) {
        var accounts = listMyProviderAccountsUseCase.execute(principal.getOrganizationId(), principal.getUserId())
                .stream().map(mapper::toResponse).toList();
        return ResponseEntity.ok(accounts);
    }

    @Operation(summary = "Unlink the caller's own account for a provider")
    @ApiResponse(responseCode = "204", description = "Account unlinked")
    @ApiResponse(responseCode = "404", description = "No active account for the provider")
    @ApiResponse(responseCode = "400", description = "Unknown provider value")
    @DeleteMapping("/{provider}")
    public ResponseEntity<Void> unlink(@AuthenticationPrincipal AndromedaUserDetails principal,
                                        @PathVariable String provider) {
        Provider parsedProvider = parseProvider(provider);
        unlinkProviderAccountUseCase.execute(principal.getOrganizationId(), principal.getUserId(), parsedProvider);
        return ResponseEntity.noContent().build();
    }

    private Provider parseProvider(String value) {
        try {
            return Provider.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new ProviderInvalidException(value);
        }
    }
}
