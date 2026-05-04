package andromeda.sei.ms.feature.user.application.port.in;

import andromeda.sei.ms.feature.user.adapter.in.web.dto.CreateUserRequest;
import andromeda.sei.ms.feature.user.adapter.in.web.dto.UserResponse;
import java.util.UUID;

public interface CreateUserUseCase {
    UserResponse execute(CreateUserRequest request, UUID organizationId);
}
