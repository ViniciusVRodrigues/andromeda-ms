package andromeda.sei.ms.shared.presentation.dto;

/**
 * Corpo do {@code 202 Accepted} de todo endpoint que dispara um sync em background.
 *
 * @param syncId id do log pai em {@code integration_sync_logs}, usado para acompanhar o progresso
 */
public record SyncStartedResponse(String syncId) {}
