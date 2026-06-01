package andromeda.sei.ms.feature.auth.presentation;

import andromeda.sei.ms.feature.auth.application.dto.CompleteAccountOutput;
import andromeda.sei.ms.feature.auth.application.dto.LoginOutput;
import andromeda.sei.ms.feature.auth.application.usecase.CompleteAccountUseCase;
import andromeda.sei.ms.feature.auth.application.usecase.LoginUseCase;
import andromeda.sei.ms.feature.auth.presentation.dto.CompleteAccountRequest;
import andromeda.sei.ms.feature.auth.presentation.dto.CompleteAccountResponse;
import andromeda.sei.ms.feature.auth.presentation.dto.LoginRequest;
import andromeda.sei.ms.feature.auth.presentation.dto.LoginResponse;
import andromeda.sei.ms.shared.infrastructure.security.JwtService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final LoginUseCase loginUseCase;
    private final CompleteAccountUseCase completeAccountUseCase;
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
}
