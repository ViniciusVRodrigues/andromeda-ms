package andromeda.sei.ms.feature.vcs.presentation.dto;

import jakarta.validation.constraints.NotNull;

public record ToggleRepositorySyncRequest(@NotNull Boolean syncEnabled) {}
