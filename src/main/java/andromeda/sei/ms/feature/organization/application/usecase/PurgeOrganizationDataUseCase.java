package andromeda.sei.ms.feature.organization.application.usecase;

import andromeda.sei.ms.feature.integration.domain.IntegrationDataWiper;
import andromeda.sei.ms.feature.integration.domain.IntegrationDataWiper.WipeScope;
import andromeda.sei.ms.feature.organization.application.port.OrganizationDataPurger;
import andromeda.sei.ms.feature.organization.domain.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Apaga de verdade os dados de uma organizacao excluida e fecha a lapide.
 *
 * <p><b>Hard delete</b>, por excecao explicita a regra de soft delete do projeto, decidida pela
 * pessoa (excluir-organizacao, fase 2): e a segunda excecao, depois do "Limpar dados" das
 * integracoes, e vale so para a exclusao da organizacao.
 *
 * <p>Roda em background ({@code @Async}), disparado por {@code DeleteOrganizationUseCase} depois
 * da lapide; o DELETE ja respondeu 204. Primeiro todos os {@link IntegrationDataWiper} com a
 * integracao inteira, depois as demais colecoes por {@code organizationId}, e so entao
 * {@code purgedAt}. Se falhar no meio, {@code purgedAt} fica nulo e o job de retomada repete
 * (tudo e delete por {@code organizationId}, idempotente).
 *
 * <p>Recusa organizacao sem lapide: o purge nunca alcanca uma organizacao viva. O id vem do
 * caso de uso de exclusao ou da lapide, nunca do cliente.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PurgeOrganizationDataUseCase {

    private final List<IntegrationDataWiper> wipers;
    private final OrganizationDataPurger purger;
    private final OrganizationRepository organizationRepository;

    @Async
    public void execute(String organizationId) {
        var tombstone = organizationRepository.findDeletedById(organizationId);
        if (tombstone.isEmpty()) {
            log.warn("Purge ignorado: organizacao {} nao tem lapide", organizationId);
            return;
        }
        if (tombstone.get().getPurgedAt() != null) {
            log.info("Purge ignorado: organizacao {} ja foi apagada", organizationId);
            return;
        }

        try {
            for (IntegrationDataWiper wiper : wipers) {
                wiper.wipe(organizationId, WipeScope.wholeIntegration());
            }
            purger.purge(organizationId);
            organizationRepository.markPurged(organizationId, Instant.now());
            log.info("Purge concluido: org={}", organizationId);
        } catch (Exception e) {
            log.error("Purge falhou, a retomada tenta de novo: org={}", organizationId, e);
        }
    }
}
