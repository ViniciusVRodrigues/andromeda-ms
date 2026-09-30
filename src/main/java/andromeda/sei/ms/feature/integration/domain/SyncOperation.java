package andromeda.sei.ms.feature.integration.domain;

public enum SyncOperation {
    SYNC_SPRINTS,
    SYNC_WORK_ITEMS,
    LIST_PROJECTS,
    LIST_REPOS,
    SYNC_REPOS,
    SYNC_COMMITS,
    SYNC_PULL_REQUESTS,
    SYNC_REVIEWS,
    /** Remocao permanente dos dados de uma integracao (wipe). */
    WIPE_DATA,
    /** Log pai de um refresh sob demanda de projeto; o status e derivado dos filhos. */
    PROJECT_REFRESH,
    /** Log pai de um sync com escopo de organizacao (trigger manual, repair ou agendado). */
    ORGANIZATION_SYNC,
    /** Log filho: o sync de um binding dentro de um refresh/sync de organizacao. */
    SYNC_BINDING
}
