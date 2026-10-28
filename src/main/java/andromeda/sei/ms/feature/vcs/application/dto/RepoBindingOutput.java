package andromeda.sei.ms.feature.vcs.application.dto;

import andromeda.sei.ms.feature.vcs.domain.VcsProvider;
import andromeda.sei.ms.feature.vcs.domain.VcsSyncState;

import java.time.Instant;
import java.util.List;

/**
 * A repository binding (branches-por-vinculo D5). {@code branches} and {@code productionBranch} are the
 * effective ones (a legacy binding follows {@code defaultBranch}); {@code branchesConfigured} is
 * {@code false} for a legacy binding. {@code backfillDays} is the organization's backfill window, for the
 * confirmation text; {@code resynced} is only set by the PATCH.
 */
public record RepoBindingOutput(String id, String projectId, String repositoryId, VcsProvider provider,
                                String displayName, boolean syncEnabled, Instant lastSyncAt,
                                String defaultBranch, List<String> branches, String productionBranch,
                                boolean branchesConfigured, boolean filterPullRequestsByBranch,
                                List<String> missingBranches, VcsSyncState syncState, int maxBranches,
                                Integer backfillDays, boolean resynced) {}
