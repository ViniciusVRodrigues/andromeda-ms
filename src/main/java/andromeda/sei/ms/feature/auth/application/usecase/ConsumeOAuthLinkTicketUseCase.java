package andromeda.sei.ms.feature.auth.application.usecase;

import andromeda.sei.ms.feature.auth.domain.OAuthLinkPurpose;
import andromeda.sei.ms.feature.auth.domain.OAuthLinkTicket;
import andromeda.sei.ms.feature.auth.domain.OAuthLinkTicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

/**
 * Consome o ticket de uso unico do popup OAuth (perfil-integracoes D1). Vazio quando o ticket e
 * invalido, ja usado, vencido ou de outro proposito.
 */
@Component
@RequiredArgsConstructor
public class ConsumeOAuthLinkTicketUseCase {

    private final OAuthLinkTicketRepository tickets;

    public Optional<OAuthLinkTicket> execute(String rawTicket, OAuthLinkPurpose purpose) {
        if (rawTicket == null || rawTicket.isBlank() || purpose == null) return Optional.empty();
        return tickets.consume(OAuthLinkTicket.hash(rawTicket), purpose, Instant.now());
    }
}
