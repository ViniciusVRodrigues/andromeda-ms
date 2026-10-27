package andromeda.sei.ms.feature.workitem.domain;

/**
 * Per-item backlog placement data served alongside the item (backlog-arvore-nivel D3).
 *
 * @param rank           rank of the item's raw type in the binding's hierarchy levels, or {@code null}
 * @param parentMissing  {@code true} only when the parent is not an active item of the project
 * @param parentRawType  raw type of the parent when it resolves, {@code null} otherwise
 */
public record BacklogItemInfo(Integer rank, boolean parentMissing, String parentRawType) {

    public static final BacklogItemInfo NONE = new BacklogItemInfo(null, false, null);
}
