package andromeda.sei.ms.shared.infrastructure.security;

import andromeda.sei.ms.feature.auth.application.usecase.ConsumeOAuthLinkTicketUseCase;
import andromeda.sei.ms.feature.auth.domain.OAuthLinkPurpose;
import andromeda.sei.ms.feature.auth.domain.OAuthLinkTicket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

/**
 * Porta do popup OAuth (perfil-integracoes D1/D3). O popup nao leva Authorization: abre
 * {@code /api/v1/auth/oauth2/authorization/{github|azure}?ticket=...}. Aqui o ticket de uso unico
 * e consumido (atomico), a sessao do fluxo e renovada (anti fixacao), o {@link LinkBinding} vai para
 * a sessao e o nonce sai no cookie {@code andromeda_oauth_nonce}. Sem ticket valido, o navegador volta
 * para a pagina de retorno do app com {@code OAUTH_TICKET_INVALID}, sem ir ao provedor.
 * Roda antes do filtro de redirect do Spring, que depois monta a URL do provedor
 * ({@link TicketOAuth2AuthorizationRequestResolver}).
 */
public class OAuthTicketAuthorizationFilter extends OncePerRequestFilter {

    public static final String AUTHORIZATION_BASE = "/api/v1/auth/oauth2/authorization/";
    public static final String NONCE_COOKIE = "andromeda_oauth_nonce";
    public static final String NONCE_COOKIE_PATH = "/api/v1/auth/oauth2";
    public static final String TICKET_INVALID = "OAUTH_TICKET_INVALID";
    static final Duration NONCE_MAX_AGE = Duration.ofMinutes(10);

    private static final SecureRandom RANDOM = new SecureRandom();

    private final ConsumeOAuthLinkTicketUseCase consumeTicket;
    private final String frontendUrl;
    private final boolean secureCookie;

    public OAuthTicketAuthorizationFilter(ConsumeOAuthLinkTicketUseCase consumeTicket, String frontendUrl,
                                          boolean secureCookie) {
        this.consumeTicket = consumeTicket;
        this.frontendUrl = frontendUrl;
        this.secureCookie = secureCookie;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return purposeFor(registrationId(request)).isEmpty();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String registrationId = registrationId(request);
        OAuthLinkPurpose purpose = purposeFor(registrationId).orElseThrow();

        Optional<OAuthLinkTicket> ticket = consumeTicket.execute(request.getParameter("ticket"), purpose);
        if (ticket.isEmpty()) {
            response.sendRedirect(UriComponentsBuilder.fromUriString(frontendUrl).path("/oauth/retorno")
                    .queryParam("provider", registrationId)
                    .queryParam("status", "error")
                    .queryParam("code", TICKET_INVALID)
                    .build().toUriString());
            return;
        }

        HttpSession session = request.getSession(true);
        request.changeSessionId();
        String nonce = newNonce();
        OAuthLinkTicket t = ticket.get();
        session.setAttribute(LinkBinding.SESSION_ATTRIBUTE, new LinkBinding(t.getOrganizationId(), t.getUserId(),
                t.getPurpose(), t.getAzureOrgUrl(), OAuthLinkTicket.hash(nonce)));
        response.addHeader(HttpHeaders.SET_COOKIE, nonceCookie(nonce).toString());
        chain.doFilter(request, response);
    }

    private ResponseCookie nonceCookie(String value) {
        return ResponseCookie.from(NONCE_COOKIE, value)
                .httpOnly(true).secure(secureCookie).sameSite("Lax")
                .path(NONCE_COOKIE_PATH).maxAge(NONCE_MAX_AGE).build();
    }

    private static String newNonce() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static Optional<OAuthLinkPurpose> purposeFor(String registrationId) {
        if ("github".equals(registrationId)) return Optional.of(OAuthLinkPurpose.GITHUB_PROFILE);
        if ("azure".equals(registrationId)) return Optional.of(OAuthLinkPurpose.AZURE_ORG_CONNECT);
        return Optional.empty();
    }

    private static String registrationId(HttpServletRequest request) {
        String path = request.getRequestURI();
        int i = path.indexOf(AUTHORIZATION_BASE);
        return i < 0 ? null : path.substring(i + AUTHORIZATION_BASE.length());
    }
}
