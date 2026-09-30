package andromeda.sei.ms.feature.sync.domain;

import andromeda.sei.ms.feature.integration.domain.Integration;

import java.util.Objects;

/**
 * Um binding a sincronizar: vinculo de work tracking (Azure Boards) ou de repositorio (GitHub,
 * Azure Repos).
 *
 * @param integration integracao dona do binding (Azure Repos fica sob {@code AZURE_DEVOPS})
 * @param bindingId   id do binding; e a chave do cooldown e do log filho
 * @param projectId   projeto Andromeda do binding; define quais metricas recalcular
 * @param kind        o que o binding sincroniza; escolhe o runner junto com a integracao
 */
public record SyncTarget(Integration integration, String bindingId, String projectId, SyncTargetKind kind) {

    public SyncTarget {
        Objects.requireNonNull(integration, "integration");
        Objects.requireNonNull(bindingId, "bindingId");
        if (kind == null) kind = SyncTargetKind.defaultFor(integration);
    }

    /** O tipo padrao da integracao: repositorio no GitHub, work tracking no Azure DevOps. */
    public SyncTarget(Integration integration, String bindingId, String projectId) {
        this(integration, bindingId, projectId, SyncTargetKind.defaultFor(integration));
    }
}
