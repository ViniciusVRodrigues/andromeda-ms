package andromeda.sei.ms.feature.auth.application.port.in;

import andromeda.sei.ms.feature.auth.adapter.in.web.dto.LoginRequest;
import andromeda.sei.ms.feature.auth.adapter.in.web.dto.LoginResponse;

public interface LoginUseCase {
    LoginResponse execute(LoginRequest request);
}
