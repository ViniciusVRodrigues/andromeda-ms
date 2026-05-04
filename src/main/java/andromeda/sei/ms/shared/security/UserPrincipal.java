package andromeda.sei.ms.shared.security;

import java.util.List;
import java.util.UUID;

public record UserPrincipal(UUID userId, UUID organizationId, List<String> permissions) {}
