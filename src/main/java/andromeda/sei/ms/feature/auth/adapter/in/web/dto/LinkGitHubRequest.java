package andromeda.sei.ms.feature.auth.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;

public record LinkGitHubRequest(@NotBlank String code) {}
