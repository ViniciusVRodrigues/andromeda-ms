package andromeda.sei.ms.feature.auth.application.port.in;

import java.util.UUID;

public interface LinkGitHubUseCase {
    void execute(String code, UUID userId, UUID organizationId);
}
