package andromeda.sei.ms.feature.github.presentation.dto;

import andromeda.sei.ms.feature.github.domain.GithubConnectionStatus;

import java.time.Instant;
import java.util.List;

public record GithubConnectionStatusResponse(
        GithubConnectionStatus status,
        String authorizedByUserId,
        Instant lastSuccessAt,
        int consecutiveFailures,
        List<GithubRepoBindingResponse> bindings
) {}
