package andromeda.sei.ms.feature.vcs.application.port;

import java.time.Instant;

/** Repository metadata as the provider reports it (neutral input DTO). */
public record VcsRemoteRepository(String externalRepoId, String displayName, String externalProjectId,
                                  String defaultBranch, Instant updatedAt) {}
