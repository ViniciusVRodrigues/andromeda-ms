package andromeda.sei.ms.feature.auth.adapter.in.web;

import andromeda.sei.ms.feature.auth.adapter.in.web.dto.LinkGitHubRequest;
import andromeda.sei.ms.feature.auth.adapter.in.web.dto.LoginRequest;
import andromeda.sei.ms.feature.auth.adapter.in.web.dto.LoginResponse;
import andromeda.sei.ms.feature.auth.application.port.in.LinkGitHubUseCase;
import andromeda.sei.ms.feature.auth.application.port.in.LoginUseCase;
import andromeda.sei.ms.shared.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Authentication endpoints")
public class AuthController {

    private final LoginUseCase loginUseCase;
    private final LinkGitHubUseCase linkGitHubUseCase;

    @PostMapping("/login")
    @Operation(summary = "Login", description = "Authenticates user with email and password, returns JWT access token")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return loginUseCase.execute(request);
    }

    @PostMapping("/github/link")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Link GitHub account", description = "Links GitHub account to the authenticated user")
    public void linkGitHub(@Valid @RequestBody LinkGitHubRequest request,
                            @AuthenticationPrincipal UserPrincipal principal) {
        linkGitHubUseCase.execute(request.code(), principal.userId(), principal.organizationId());
    }
}
