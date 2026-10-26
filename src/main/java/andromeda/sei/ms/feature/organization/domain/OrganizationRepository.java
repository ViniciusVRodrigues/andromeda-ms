package andromeda.sei.ms.feature.organization.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OrganizationRepository {

    Optional<Organization> findById(String id);

    Optional<Organization> findBySlug(String slug);

    boolean existsBySlug(String slug);

    Organization save(Organization organization);

    /**
     * Organizacoes ativas cujo sync agendado venceu no {@code tick}: frequencia diferente de
     * {@code OFF} e {@code lastScheduledSyncAt} ausente ou {@code <= tick - intervalo}.
     * Consulta de manutencao do scheduler, sem tenant por construcao.
     */
    List<Organization> findAllDueForScheduledSync(Instant tick);

    /**
     * Lapide de uma organizacao excluida (com {@code deletedAt}), ativa ou nao. Ao contrario de
     * {@link #findById}, que so enxerga organizacoes ativas.
     */
    Optional<Organization> findDeletedById(String id);

    /**
     * Lapides cujo purge ainda nao terminou: {@code deletedAt <= deletedBefore} e sem
     * {@code purgedAt}. Consulta de manutencao da retomada, sem tenant por construcao.
     */
    List<Organization> findPendingPurge(Instant deletedBefore);

    /**
     * Fecha o purge: reduz o documento a lapide (id, name, slug, active=false, deletedAt,
     * deletedBy, purgedAt, createdAt, updatedAt, version) e zera o resto.
     */
    void markPurged(String id, Instant purgedAt);
}
