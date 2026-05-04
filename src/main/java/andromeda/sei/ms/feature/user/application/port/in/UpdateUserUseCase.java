package andromeda.sei.ms.feature.user.application.port.in;

import andromeda.sei.ms.feature.user.adapter.in.web.dto.UpdateUserRequest;
import andromeda.sei.ms.feature.user.adapter.in.web.dto.UserResponse;
import java.util.UUID;

public interface UpdateUserUseCase {
    UserResponse execute(UUID userId, UpdateUserRequest request, UUID organizationId);
}
