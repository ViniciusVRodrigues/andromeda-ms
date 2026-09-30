package andromeda.sei.ms.feature.sync.application;

import andromeda.sei.ms.feature.integration.domain.Integration;
import andromeda.sei.ms.feature.integration.domain.SyncOperation;
import andromeda.sei.ms.feature.integration.domain.SyncTrigger;
import andromeda.sei.ms.feature.sync.domain.SyncTarget;

import java.util.List;
import java.util.Objects;

/**
 * Pedido de sync ao orquestrador.
 *
 * @param operation       operacao do log pai: {@code PROJECT_REFRESH} ou {@code ORGANIZATION_SYNC}
 * @param integration     integracao do log pai; {@code null} quando o pedido cruza integracoes
 * @param projectId       projeto do log pai; {@code null} no escopo de organizacao
 * @param resetWatermarks zera os watermarks antes do sync (repair)
 */
public record SyncRequest(String organizationId, SyncOperation operation, Integration integration,
                          String projectId, List<SyncTarget> targets, SyncTrigger trigger,
                          boolean resetWatermarks) {

    public SyncRequest {
        Objects.requireNonNull(organizationId, "organizationId");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(trigger, "trigger");
        targets = List.copyOf(targets);
    }

    public static SyncRequest projectRefresh(String organizationId, String projectId, List<SyncTarget> targets) {
        return new SyncRequest(organizationId, SyncOperation.PROJECT_REFRESH, null, projectId, targets,
                SyncTrigger.MANUAL, false);
    }

    public static SyncRequest organization(String organizationId, Integration integration, List<SyncTarget> targets,
                                           SyncTrigger trigger) {
        return new SyncRequest(organizationId, SyncOperation.ORGANIZATION_SYNC, integration, null, targets,
                trigger, false);
    }

    public SyncRequest withResetWatermarks() {
        return new SyncRequest(organizationId, operation, integration, projectId, targets, trigger, true);
    }
}
