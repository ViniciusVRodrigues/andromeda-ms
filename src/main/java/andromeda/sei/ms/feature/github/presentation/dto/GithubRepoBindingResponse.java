package andromeda.sei.ms.feature.github.presentation.dto;

import java.time.Instant;

public record GithubRepoBindingResponse(
        String id,
        String projectId,
        String repoId,
        boolean syncEnabled,
        Instant lastSyncAt
) {}
