package andromeda.sei.ms.feature.sync.application.dto;

import java.time.Instant;

/**
 * Frescura dos dados que uma tela le, incluida nas respostas de dashboard, repositorios e backlog.
 *
 * @param oldestSyncAt   menor {@code lastSyncAt} entre os bindings ativos e sincronizaveis do
 *                       projeto que ja sincronizaram ao menos uma vez; {@code null} quando nenhum
 *                       binding sincronizou ainda
 * @param refreshRunning ha um refresh/sync rodando agora para o projeto
 */
public record DataFreshness(Instant oldestSyncAt, boolean refreshRunning) {}
