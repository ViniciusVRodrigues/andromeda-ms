package andromeda.sei.ms.feature.auth.presentation;

import andromeda.sei.ms.feature.auth.application.dto.CompleteAccountOutput;
import andromeda.sei.ms.feature.auth.application.dto.GetCurrentUserInput;
import andromeda.sei.ms.feature.auth.application.dto.GetCurrentUserOutput;
import andromeda.sei.ms.feature.auth.application.dto.LoginOutput;
import andromeda.sei.ms.feature.auth.application.usecase.CompleteAccountUseCase;
import andromeda.sei.ms.feature.auth.application.usecase.ConfirmEmailVerificationUseCase;
import andromeda.sei.ms.feature.auth.application.usecase.GetCurrentUserUseCase;
import andromeda.sei.ms.feature.auth.application.usecase.LoginUseCase;
import andromeda.sei.ms.feature.auth.application.usecase.SendEmailVerificationUseCase;
import andromeda.sei.ms.feature.auth.presentation.dto.CompleteAccountRequest;
import andromeda.sei.ms.feature.auth.presentation.dto.CompleteAccountResponse;
import andromeda.sei.ms.feature.auth.presentation.dto.ConfirmEmailVerificationRequest;
import andromeda.sei.ms.feature.auth.presentation.dto.CurrentUserResponse;
import andromeda.sei.ms.feature.auth.presentation.dto.LoginRequest;
import andromeda.sei.ms.feature.auth.presentation.dto.LoginResponse;
import andromeda.sei.ms.feature.auth.presentation.dto.SendEmailVerificationRequest;
import andromeda.sei.ms.shared.infrastructure.security.AndromedaUserDetails;
import andromeda.sei.ms.shared.infrastructure.security.JwtService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

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

    @GetMapping("/me")
    public ResponseEntity<CurrentUserResponse> me(@AuthenticationPrincipal AndromedaUserDetails principal) {
        GetCurrentUserOutput output = getCurrentUserUseCase.execute(
                new GetCurrentUserInput(principal.getUserId(), principal.getOrganizationId(), principal.getPermissions())
        );
        return ResponseEntity.ok(mapper.toCurrentUserResponse(output));
    }
}
