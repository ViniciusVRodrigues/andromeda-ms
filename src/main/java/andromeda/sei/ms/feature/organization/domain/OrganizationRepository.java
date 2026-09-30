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
}
