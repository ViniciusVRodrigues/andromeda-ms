package andromeda.sei.ms.feature.github.application.dto;

import andromeda.sei.ms.feature.github.domain.GithubConnectionStatus;

import java.time.Instant;
import java.util.List;

public record GithubConnectionStatusOutput(
        GithubConnectionStatus status,
        String authorizedByUserId,
        Instant lastSuccessAt,
        int consecutiveFailures,
        List<BindingSummary> bindings
) {
    public record BindingSummary(String id, String projectId, String repoId, boolean syncEnabled, Instant lastSyncAt) {}
}
