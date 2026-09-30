package andromeda.sei.ms.feature.vcs.application.dto;

import andromeda.sei.ms.feature.vcs.domain.VcsProvider;

import java.time.Instant;

public record RepoBindingOutput(String id, String projectId, String repositoryId, VcsProvider provider,
                                String displayName, boolean syncEnabled, Instant lastSyncAt) {}
