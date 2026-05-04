package andromeda.sei.ms.feature.user.application.port.in;

import java.util.UUID;

public interface DeactivateUserUseCase {
    void execute(UUID userId, UUID organizationId);
}
