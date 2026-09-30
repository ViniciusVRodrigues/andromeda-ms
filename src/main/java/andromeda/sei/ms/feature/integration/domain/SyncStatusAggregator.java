package andromeda.sei.ms.feature.integration.domain;

import java.util.Collection;

/**
 * Deriva o status de um sync pai (refresh de projeto ou sync de organizacao) a partir dos
 * status dos seus filhos por binding.
 *
 * <p>A regra e conservadora: um refresh so e {@code SUCCESS} quando nenhum binding falhou nem
 * teve item com falha. {@code PARTIAL} de um filho conta como falha parcial, entao contamina o
 * pai para {@code PARTIAL} mesmo que todos os outros filhos tenham dado certo.
 */
public final class SyncStatusAggregator {

    private SyncStatusAggregator() {}

    /**
     * @param childStatuses status dos filhos; vazio significa que nada precisou ser sincronizado
     * @return {@code RUNNING} enquanto algum filho roda; {@code SUCCESS} se todos deram certo;
     *         {@code FAILED} se todos falharam; {@code PARTIAL} em qualquer mistura
     */
    public static SyncStatus aggregate(Collection<SyncStatus> childStatuses) {
        if (childStatuses.isEmpty()) {
            return SyncStatus.SUCCESS;
        }
        if (childStatuses.contains(SyncStatus.RUNNING)) {
            return SyncStatus.RUNNING;
        }
        if (childStatuses.stream().allMatch(s -> s == SyncStatus.SUCCESS)) {
            return SyncStatus.SUCCESS;
        }
        if (childStatuses.stream().allMatch(s -> s == SyncStatus.FAILED)) {
            return SyncStatus.FAILED;
        }
        return SyncStatus.PARTIAL;
    }
}
