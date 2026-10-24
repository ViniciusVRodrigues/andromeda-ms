package andromeda.sei.ms.feature.auth.domain;

import java.time.Instant;
import java.util.Optional;

public interface OAuthLinkTicketRepository {

    OAuthLinkTicket save(OAuthLinkTicket ticket);

    /**
     * Consome o ticket de forma atomica: so devolve algo se ele existe, nao foi consumido, nao venceu e
     * tem o proposito pedido. O hash e a chave global (o ticket e opaco e quem consome ainda nao sabe a org).
     */
    Optional<OAuthLinkTicket> consume(String ticketHash, OAuthLinkPurpose purpose, Instant now);
}
