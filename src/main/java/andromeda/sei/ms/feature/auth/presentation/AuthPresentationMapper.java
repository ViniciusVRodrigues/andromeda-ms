package andromeda.sei.ms.feature.auth.presentation;

import andromeda.sei.ms.feature.auth.application.dto.CompleteAccountInput;
import andromeda.sei.ms.feature.auth.application.dto.CompleteAccountOutput;
import andromeda.sei.ms.feature.auth.application.dto.LoginInput;
import andromeda.sei.ms.feature.auth.application.dto.LoginOutput;
import andromeda.sei.ms.feature.auth.presentation.dto.CompleteAccountRequest;
import andromeda.sei.ms.feature.auth.presentation.dto.CompleteAccountResponse;
import andromeda.sei.ms.feature.auth.presentation.dto.LoginRequest;
import andromeda.sei.ms.feature.auth.presentation.dto.LoginResponse;
import org.springframework.stereotype.Component;

@Component
public class AuthPresentationMapper {

    public LoginInput toLoginInput(LoginRequest request) {
        return new LoginInput(request.email(), request.password());
    }

    public LoginResponse toLoginResponse(LoginOutput output, String token) {
        return new LoginResponse(token, output.expiresInMs());
    }

    public CompleteAccountInput toCompleteInput(CompleteAccountRequest request) {
        return new CompleteAccountInput(request.token(), request.name(), request.password());
    }

    public CompleteAccountResponse toCompleteResponse(CompleteAccountOutput output, String token) {
        return new CompleteAccountResponse(token, output.expiresInMs());
    }
}
