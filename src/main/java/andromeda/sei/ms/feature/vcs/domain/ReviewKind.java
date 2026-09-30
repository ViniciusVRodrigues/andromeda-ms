package andromeda.sei.ms.feature.vcs.domain;

/**
 * Normalized kind of a review signal (design D3). Each provider maps its own payload into one of
 * these, so {@link ReviewMetricsPolicy} never sees a provider-specific state.
 */
public enum ReviewKind {
    APPROVED,
    APPROVED_WITH_SUGGESTIONS,
    CHANGES_REQUESTED,
    REJECTED,
    COMMENTED;

    /** An approval, with or without suggestions — what time to first approval measures. */
    public boolean isApproval() {
        return this == APPROVED || this == APPROVED_WITH_SUGGESTIONS;
    }
}
