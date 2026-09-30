package andromeda.sei.ms.feature.sync.domain;

import andromeda.sei.ms.feature.integration.domain.SyncStatus;

/**
 * Desfecho do sync de um binding, gravado no log filho.
 *
 * @param status         {@code SUCCESS}, {@code PARTIAL} (algum item falhou) ou {@code FAILED}
 * @param errorCode      codigo {@code ENTITY_REASON} quando nao foi {@code SUCCESS}
 * @param errorMessage   detalhe legivel da falha
 * @param itemsProcessed itens gravados
 * @param itemsFailed    itens que falharam
 */
public record BindingSyncOutcome(SyncTarget target, SyncStatus status, String errorCode, String errorMessage,
                                 int itemsProcessed, int itemsFailed) {

    public static BindingSyncOutcome success(SyncTarget target, int itemsProcessed) {
        return new BindingSyncOutcome(target, SyncStatus.SUCCESS, null, null, itemsProcessed, 0);
    }

    public static BindingSyncOutcome partial(SyncTarget target, String errorCode, int itemsProcessed, int itemsFailed) {
        return new BindingSyncOutcome(target, SyncStatus.PARTIAL, errorCode,
                itemsFailed + " item(s) failed to sync", itemsProcessed, itemsFailed);
    }

    public static BindingSyncOutcome failed(SyncTarget target, String errorCode, String errorMessage) {
        return new BindingSyncOutcome(target, SyncStatus.FAILED, errorCode, errorMessage, 0, 0);
    }

    /** A conexao respondeu: so {@code FAILED} conta como falha para a saude da conexao. */
    public boolean reachedProvider() {
        return status != SyncStatus.FAILED;
    }
}
