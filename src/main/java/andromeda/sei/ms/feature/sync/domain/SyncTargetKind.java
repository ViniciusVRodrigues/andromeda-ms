package andromeda.sei.ms.feature.sync.domain;

import andromeda.sei.ms.feature.integration.domain.Integration;

/**
 * What a binding syncs. Azure DevOps has both kinds under one integration (Boards and, since
 * azure-repos-provider, Azure Repos), so the orchestrator picks a runner by integration and kind.
 */
public enum SyncTargetKind {
    /** Sprints and work items (Azure Boards). */
    WORK_TRACKING,
    /** A version-control repository (GitHub, Azure Repos). */
    REPOSITORY;

    /** The only kind an integration had before azure-repos-provider: GitHub syncs repositories. */
    public static SyncTargetKind defaultFor(Integration integration) {
        return integration == Integration.GITHUB ? REPOSITORY : WORK_TRACKING;
    }
}
