package andromeda.sei.ms.feature.azure.application.dto;

import andromeda.sei.ms.feature.azure.domain.AzureProjectBinding;
import andromeda.sei.ms.feature.integration.domain.SyncStatus;

/**
 * Resultado de um sync de work items de um binding.
 *
 * @param binding   o binding tal como persistido (version atualizada) — quem encadeia outro
 *                  save sobre o mesmo binding deve seguir com esta instancia
 * @param status    {@code SUCCESS}, ou {@code PARTIAL} quando algum item falhou mas outros foram
 *                  gravados, ou {@code FAILED} quando nenhum item pode ser gravado
 * @param processed itens gravados
 * @param failed    itens que falharam
 */
public record AzureWorkItemsSyncResult(AzureProjectBinding binding, SyncStatus status, int processed, int failed) {}
