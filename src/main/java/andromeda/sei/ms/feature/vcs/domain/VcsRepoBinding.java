package andromeda.sei.ms.feature.vcs.domain;

import andromeda.sei.ms.feature.vcs.domain.exception.RepoBranchesInvalidException;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;

/**
 * Links an Andromeda project to a repository by its <strong>internal</strong> id. An active binding
 * of a repository exists for at most one project per organization.
 *
 * <p>{@code lastSyncAt} is the read watermark of the repository's sync: the instant the last
 * successful run <em>started</em>.
 *
 * <p>Branches (branches-por-vinculo D1): {@code branches} (1 to {@link #MAX_BRANCHES}, short names) and
 * {@code productionBranch} are {@code null} on bindings created before the feature, which then follow
 * the repository's default branch exactly as before; use {@link #effectiveBranches} and
 * {@link #effectiveProductionBranch}.
 */
public class VcsRepoBinding {

    public static final int MAX_BRANCHES = 10;
    /** A {@code RUNNING} state older than this is read as {@code FAILED} (a dead process must not lock the binding). */
    public static final Duration RUNNING_TIMEOUT = Duration.ofMinutes(30);

    private String id;
    private String organizationId;
    private String projectId;
    private String repositoryId;
    private VcsProvider provider;
    private boolean syncEnabled;
    private Instant lastSyncAt;
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;
    private Long version;
    private List<String> branches;
    private String productionBranch;
    private boolean filterPullRequestsByBranch;
    private List<String> missingBranches;
    private VcsSyncState syncState;
    private Instant syncStateAt;

    public VcsRepoBinding(String id, String organizationId, String projectId, String repositoryId,
                          VcsProvider provider, boolean syncEnabled) {
        this.id = id;
        this.organizationId = organizationId;
        this.projectId = projectId;
        this.repositoryId = repositoryId;
        this.provider = provider;
        this.syncEnabled = syncEnabled;
        this.active = true;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getOrganizationId() { return organizationId; }
    public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }

    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }

    public String getRepositoryId() { return repositoryId; }
    public void setRepositoryId(String repositoryId) { this.repositoryId = repositoryId; }

    public VcsProvider getProvider() { return provider; }
    public void setProvider(VcsProvider provider) { this.provider = provider; }

    public boolean isSyncEnabled() { return syncEnabled; }
    public void setSyncEnabled(boolean syncEnabled) { this.syncEnabled = syncEnabled; }

    public Instant getLastSyncAt() { return lastSyncAt; }
    public void setLastSyncAt(Instant lastSyncAt) { this.lastSyncAt = lastSyncAt; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    /** Stored branches; {@code null} on a legacy binding. */
    public List<String> getBranches() { return branches; }
    public void setBranches(List<String> branches) { this.branches = branches; }

    public String getProductionBranch() { return productionBranch; }
    public void setProductionBranch(String productionBranch) { this.productionBranch = productionBranch; }

    public boolean isFilterPullRequestsByBranch() { return filterPullRequestsByBranch; }
    public void setFilterPullRequestsByBranch(boolean filterPullRequestsByBranch) {
        this.filterPullRequestsByBranch = filterPullRequestsByBranch;
    }

    public List<String> getMissingBranches() { return missingBranches == null ? List.of() : missingBranches; }
    public void setMissingBranches(List<String> missingBranches) { this.missingBranches = missingBranches; }

    public VcsSyncState getSyncState() { return syncState; }
    public void setSyncState(VcsSyncState syncState) { this.syncState = syncState; }

    public Instant getSyncStateAt() { return syncStateAt; }
    public void setSyncStateAt(Instant syncStateAt) { this.syncStateAt = syncStateAt; }

    /** Branches were chosen on this binding (an empty list counts as not chosen). */
    public boolean isBranchesConfigured() {
        return branches != null && !branches.isEmpty();
    }

    /** The branches the sync reads: the chosen ones, or only the repository's default branch for a legacy binding. */
    public List<String> effectiveBranches(String defaultBranch) {
        if (isBranchesConfigured()) return List.copyOf(branches);
        return defaultBranch == null || defaultBranch.isBlank() ? List.of() : List.of(defaultBranch);
    }

    /** The branch delivery metrics compare pull request targets with. */
    public String effectiveProductionBranch(String defaultBranch) {
        if (isBranchesConfigured() && productionBranch != null && !productionBranch.isBlank()) return productionBranch;
        return defaultBranch;
    }

    /** Stored state, or derived for legacy bindings; a {@code RUNNING} past the timeout reads as {@code FAILED}. */
    public VcsSyncState effectiveSyncState(Instant now) {
        if (syncState == null) return lastSyncAt == null ? VcsSyncState.PENDING : VcsSyncState.OK;
        if (syncState == VcsSyncState.RUNNING && isRunningExpired(now)) return VcsSyncState.FAILED;
        return syncState;
    }

    /** Syncing right now (and not expired). */
    public boolean isSyncRunning(Instant now) {
        return effectiveSyncState(now) == VcsSyncState.RUNNING;
    }

    private boolean isRunningExpired(Instant now) {
        return syncStateAt != null && syncStateAt.plus(RUNNING_TIMEOUT).isBefore(now);
    }

    /** Same set as the effective branches, ignoring order. */
    public boolean sameBranchSet(List<String> candidate, String defaultBranch) {
        return new HashSet<>(effectiveBranches(defaultBranch)).equals(new HashSet<>(candidate));
    }

    /** 1 to {@link #MAX_BRANCHES} non-blank distinct names, and a production branch among them. */
    public static void validateBranches(List<String> branches, String productionBranch) {
        if (branches == null || branches.isEmpty()) throw new RepoBranchesInvalidException("at least one branch");
        if (branches.size() > MAX_BRANCHES) {
            throw new RepoBranchesInvalidException("at most " + MAX_BRANCHES + " branches");
        }
        if (branches.stream().anyMatch(name -> name == null || name.isBlank())) {
            throw new RepoBranchesInvalidException("blank branch name");
        }
        if (new HashSet<>(branches).size() != branches.size()) {
            throw new RepoBranchesInvalidException("repeated branch");
        }
        if (productionBranch == null || !branches.contains(productionBranch)) {
            throw new RepoBranchesInvalidException("the production branch must be one of the chosen branches");
        }
    }
}
