package andromeda.sei.ms.feature.auth.presentation;

import andromeda.sei.ms.feature.auth.application.dto.CompleteAccountOutput;
import andromeda.sei.ms.feature.auth.application.dto.GetCurrentUserInput;
import andromeda.sei.ms.feature.auth.application.dto.GetCurrentUserOutput;
import andromeda.sei.ms.feature.auth.application.dto.LoginOutput;
import andromeda.sei.ms.feature.auth.application.usecase.CompleteAccountUseCase;
import andromeda.sei.ms.feature.auth.application.usecase.ConfirmEmailVerificationUseCase;
import andromeda.sei.ms.feature.auth.application.usecase.ConfirmPasswordResetUseCase;
import andromeda.sei.ms.feature.auth.application.usecase.RequestPasswordResetUseCase;
import andromeda.sei.ms.feature.auth.application.usecase.GetCurrentUserUseCase;
import andromeda.sei.ms.feature.auth.application.usecase.LoginUseCase;
import andromeda.sei.ms.feature.auth.application.usecase.SendEmailVerificationUseCase;
import andromeda.sei.ms.feature.auth.presentation.dto.CompleteAccountRequest;
import andromeda.sei.ms.feature.auth.presentation.dto.CompleteAccountResponse;
import andromeda.sei.ms.feature.auth.presentation.dto.ConfirmEmailVerificationRequest;
import andromeda.sei.ms.feature.auth.presentation.dto.ConfirmPasswordResetRequest;
import andromeda.sei.ms.feature.auth.presentation.dto.CurrentUserResponse;
import andromeda.sei.ms.feature.auth.presentation.dto.RequestPasswordResetRequest;
import andromeda.sei.ms.feature.auth.presentation.dto.LoginRequest;
import andromeda.sei.ms.feature.auth.presentation.dto.LoginResponse;
import andromeda.sei.ms.feature.auth.presentation.dto.SendEmailVerificationRequest;
import andromeda.sei.ms.shared.infrastructure.ratelimit.RateLimiter;
import andromeda.sei.ms.shared.infrastructure.security.AndromedaUserDetails;
import andromeda.sei.ms.shared.infrastructure.security.JwtService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final RequestPasswordResetUseCase requestPasswordResetUseCase;
    private final ConfirmPasswordResetUseCase confirmPasswordResetUseCase;
    private final RateLimiter rateLimiter;

    private final LoginUseCase loginUseCase;
    private final CompleteAccountUseCase completeAccountUseCase;
    private final GetCurrentUserUseCase getCurrentUserUseCase;
    private final SendEmailVerificationUseCase sendEmailVerificationUseCase;
    private final ConfirmEmailVerificationUseCase confirmEmailVerificationUseCase;
    private final AuthPresentationMapper mapper;
    private final JwtService jwtService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginOutput output = loginUseCase.execute(mapper.toLoginInput(request));
        String token = jwtService.generate(output.userId(), output.orgId(), output.permissionCodes());
        return ResponseEntity.ok(mapper.toLoginResponse(output, token));
    }

    @PostMapping("/invite/complete")
    public ResponseEntity<CompleteAccountResponse> completeAccount(@Valid @RequestBody CompleteAccountRequest request) {
        CompleteAccountOutput output = completeAccountUseCase.execute(mapper.toCompleteInput(request));
        String token = jwtService.generate(output.userId(), output.orgId(), output.permissionCodes());
        return ResponseEntity.ok(mapper.toCompleteResponse(output, token));
    }

    @PostMapping("/email-verification")
    public ResponseEntity<Void> sendEmailVerification(@Valid @RequestBody SendEmailVerificationRequest request) {
        sendEmailVerificationUseCase.execute(request.email());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/email-verification/confirm")
    public ResponseEntity<Map<String, Boolean>> confirmEmailVerification(@Valid @RequestBody ConfirmEmailVerificationRequest request) {
        confirmEmailVerificationUseCase.execute(request.email(), request.code());
        return ResponseEntity.ok(Map.of("verified", true));
    }

    @Operation(summary = "Solicitar redefinição de senha",
            description = "Responde 202 sempre — inclusive para e-mail sem conta, para não revelar quais endereços existem.")
    @ApiResponse(responseCode = "202", description = "Solicitação recebida")
    @PostMapping("/password-reset")
    public ResponseEntity<Void> requestPasswordReset(@Valid @RequestBody RequestPasswordResetRequest request,
                                                     HttpServletRequest http) {
        rateLimiter.check("pwd-reset:ip:" + clientIp(http), 10, Duration.ofMinutes(15));
        rateLimiter.check("pwd-reset:email:" + request.email().trim().toLowerCase(), 5, Duration.ofMinutes(15));
        requestPasswordResetUseCase.execute(request.email());
        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "Redefinir a senha com o token recebido por e-mail")
    @ApiResponse(responseCode = "204", description = "Senha redefinida")
    @ApiResponse(responseCode = "400", description = "Token inválido, expirado ou já usado; ou senha fora das regras")
    @PostMapping("/password-reset/confirm")
    public ResponseEntity<Void> confirmPasswordReset(@Valid @RequestBody ConfirmPasswordResetRequest request,
                                                     HttpServletRequest http) {
        rateLimiter.check("pwd-reset-confirm:ip:" + clientIp(http), 20, Duration.ofMinutes(15));
        confirmPasswordResetUseCase.execute(request.token(), request.password());
        return ResponseEntity.noContent().build();
    }

    /** Respeita X-Forwarded-For: a aplicacao roda atras de nginx/Cloudflare. */
    private String clientIp(HttpServletRequest http) {
        String forwarded = http.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return http.getRemoteAddr();
    }

    @GetMapping("/me")
    public ResponseEntity<CurrentUserResponse> me(@AuthenticationPrincipal AndromedaUserDetails principal) {
        GetCurrentUserOutput output = getCurrentUserUseCase.execute(
                new GetCurrentUserInput(principal.getUserId(), principal.getOrganizationId(), principal.getPermissions())
        );
        return ResponseEntity.ok(mapper.toCurrentUserResponse(output));
    }
}
