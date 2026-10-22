package andromeda.sei.ms.feature.auth.presentation;

import andromeda.sei.ms.feature.auth.application.dto.CompleteAccountInput;
import andromeda.sei.ms.feature.auth.application.dto.CompleteAccountOutput;
import andromeda.sei.ms.feature.auth.application.dto.GetCurrentUserOutput;
import andromeda.sei.ms.feature.auth.application.dto.LoginInput;
import andromeda.sei.ms.feature.auth.application.dto.LoginOutput;
import andromeda.sei.ms.feature.auth.presentation.dto.CompleteAccountRequest;
import andromeda.sei.ms.feature.auth.presentation.dto.CompleteAccountResponse;
import andromeda.sei.ms.feature.auth.presentation.dto.CurrentUserResponse;
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
        return new CompleteAccountInput(request.token(), request.name(), request.password(), request.funnelId());
    }

    public CompleteAccountResponse toCompleteResponse(CompleteAccountOutput output, String token) {
        return new CompleteAccountResponse(token, output.expiresInMs());
    }

    public CurrentUserResponse toCurrentUserResponse(GetCurrentUserOutput output) {
        return new CurrentUserResponse(
                output.userId(),
                output.name(),
                output.email(),
                output.organizationId(),
                output.orgName(),
                output.orgSlug(),
                output.permissions(),
                output.orgPermissions(),
                output.projectPermissions(),
                output.roleId(),
                output.roleLabel(),
                output.seniority(),
                output.memberSince()
        );
    }
}
