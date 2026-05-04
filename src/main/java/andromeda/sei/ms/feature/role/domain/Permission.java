package andromeda.sei.ms.feature.role.domain;

import java.util.UUID;

public record Permission(UUID id, String code, String description, String module) {}
