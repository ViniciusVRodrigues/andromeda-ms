package andromeda.sei.ms.feature.metrics.domain;

/**
 * Where a delivery metric comes from (delivery-metrics). Every value is a proxy — there is no
 * deploy data — so the source is always stated next to the number.
 */
public enum DeliveryMethod {
    /** Computed from pull requests merged into the default branch of the bound repositories. */
    VCS_PROXY,
    /** Computed from completed work items, when no repository has merges in the window. */
    BOARDS_PROXY,
    /** Neither source has data in the window. */
    UNAVAILABLE
}
