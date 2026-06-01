package andromeda.sei.ms.feature.auth.application.dto;

import java.util.List;

public record LoginOutput(String userId, String orgId, List<String> permissionCodes, long expiresInMs) {
}
