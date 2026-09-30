package andromeda.sei.ms.feature.sync.application.runner;

import andromeda.sei.ms.feature.integration.domain.Integration;
import andromeda.sei.ms.feature.sync.domain.BindingSyncOutcome;
import andromeda.sei.ms.feature.sync.domain.SyncTargetKind;

import java.util.function.Consumer;

/**
 * Executa o sync dos bindings de uma integracao para uma organizacao. Cada provedor (Azure
 * DevOps, GitHub, Azure Repos) registra uma implementacao, e o orquestrador escolhe pela
 * {@link #integration()} e pelo {@link #kind()}.
 *
 * <p>Contrato: nunca lanca por falha de um binding — reporta um {@link BindingSyncOutcome} por
 * alvo, na ordem em que terminam, e aplica a regra de saude da conexao ao fim do lote.
 */
public interface BindingSyncRunner {

    Integration integration();

    /** O tipo de binding que este runner sincroniza; o orquestrador escolhe por (integracao, tipo). */
    default SyncTargetKind kind() {
        return SyncTargetKind.defaultFor(integration());
    }

    void run(BindingSyncBatch batch, Consumer<BindingSyncOutcome> onOutcome);
}
