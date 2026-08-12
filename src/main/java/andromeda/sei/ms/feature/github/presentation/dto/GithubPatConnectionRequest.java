package andromeda.sei.ms.feature.github.presentation.dto;

import jakarta.validation.constraints.NotBlank;

public record GithubPatConnectionRequest(@NotBlank String personalAccessToken) {}
