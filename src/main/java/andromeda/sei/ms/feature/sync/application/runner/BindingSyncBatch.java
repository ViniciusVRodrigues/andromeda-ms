package andromeda.sei.ms.feature.sync.application.runner;

import andromeda.sei.ms.feature.integration.domain.SyncTrigger;
import andromeda.sei.ms.feature.sync.domain.SyncTarget;

import java.time.Instant;
import java.util.List;

/**
 * Lote de bindings de uma integracao a sincronizar numa organizacao.
 *
 * @param backfillFrom    limite inferior do primeiro sync de um binding (janela de backfill)
 * @param resetWatermarks zera o watermark antes de sincronizar (repair): o binding volta a um
 *                        primeiro sync, limitado pela janela de backfill
 */
public record BindingSyncBatch(String organizationId, List<SyncTarget> targets, SyncTrigger trigger,
                               Instant backfillFrom, boolean resetWatermarks) {}
