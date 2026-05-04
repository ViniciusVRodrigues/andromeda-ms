package andromeda.sei.ms.feature.user.adapter.in.web.dto;

import java.util.UUID;

public record UpdateUserRequest(String name, UUID roleId) {}
