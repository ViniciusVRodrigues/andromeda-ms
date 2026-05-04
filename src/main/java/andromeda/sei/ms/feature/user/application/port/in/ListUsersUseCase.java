package andromeda.sei.ms.feature.user.application.port.in;

import andromeda.sei.ms.feature.user.adapter.in.web.dto.UserResponse;
import java.util.List;
import java.util.UUID;

public interface ListUsersUseCase {
    List<UserResponse> execute(UUID organizationId);
}
