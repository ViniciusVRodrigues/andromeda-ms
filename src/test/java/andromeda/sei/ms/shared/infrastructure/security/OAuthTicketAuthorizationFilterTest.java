package andromeda.sei.ms.shared.infrastructure.security;

import andromeda.sei.ms.feature.auth.application.usecase.ConsumeOAuthLinkTicketUseCase;
import andromeda.sei.ms.feature.auth.domain.OAuthLinkPurpose;
import andromeda.sei.ms.feature.auth.domain.OAuthLinkTicket;
import andromeda.sei.ms.feature.auth.domain.OAuthLinkTicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** Tasks 3.2/3.3: o ticket de uso unico abre o popup; sem ele, volta ao app com OAUTH_TICKET_INVALID. */
class OAuthTicketAuthorizationFilterTest {

    private static final String FRONT = "http://localhost:5173";

    private final InMemoryTickets tickets = new InMemoryTickets();
    private OAuthTicketAuthorizationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new OAuthTicketAuthorizationFilter(new ConsumeOAuthLinkTicketUseCase(tickets), FRONT, true);
    }

    @Test
    void validTicket_bindsSession_emitsNonceCookie_andContinuesToTheProvider() throws Exception {
        tickets.add("raw-1", "org-1", "user-1", OAuthLinkPurpose.GITHUB_PROFILE, null, Instant.now().plusSeconds(60));
        MockHttpServletRequest request = request("github", "raw-1");
        String oldSessionId = request.getSession(true).getId();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(200);
        LinkBinding binding = (LinkBinding) request.getSession().getAttribute(LinkBinding.SESSION_ATTRIBUTE);
        assertThat(binding.userId()).isEqualTo("user-1");
        assertThat(binding.organizationId()).isEqualTo("org-1");
        assertThat(binding.purpose()).isEqualTo(OAuthLinkPurpose.GITHUB_PROFILE);
        assertThat(request.getSession().getId()).isNotEqualTo(oldSessionId);

        String cookie = response.getHeader("Set-Cookie");
        assertThat(cookie).startsWith("andromeda_oauth_nonce=")
                .contains("HttpOnly", "Secure", "SameSite=Lax", "Path=/api/v1/auth/oauth2", "Max-Age=600");
        String nonce = cookie.substring("andromeda_oauth_nonce=".length(), cookie.indexOf(';'));
        assertThat(nonce).hasSize(43);
        assertThat(binding.nonceHash()).isEqualTo(OAuthLinkTicket.hash(nonce)).isNotEqualTo(nonce);
    }

    @Test
    void localProfile_cookieIsNotSecure() throws Exception {
        OAuthTicketAuthorizationFilter local = new OAuthTicketAuthorizationFilter(
                new ConsumeOAuthLinkTicketUseCase(tickets), FRONT, false);
        tickets.add("raw-1", "org-1", "user-1", OAuthLinkPurpose.GITHUB_PROFILE, null, Instant.now().plusSeconds(60));
        MockHttpServletResponse response = new MockHttpServletResponse();

        local.doFilter(request("github", "raw-1"), response, new MockFilterChain());

        assertThat(response.getHeader("Set-Cookie")).contains("HttpOnly", "SameSite=Lax").doesNotContain("Secure");
    }

    @Test
    void azureTicket_carriesTheOrgUrlOfTheTicket_notTheQuery() throws Exception {
        tickets.add("raw-az", "org-1", "user-1", OAuthLinkPurpose.AZURE_ORG_CONNECT,
                "https://dev.azure.com/acme", Instant.now().plusSeconds(60));
        MockHttpServletRequest request = request("azure", "raw-az");
        request.setParameter("orgUrl", "https://dev.azure.com/evil");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        LinkBinding binding = (LinkBinding) request.getSession().getAttribute(LinkBinding.SESSION_ATTRIBUTE);
        assertThat(binding.azureOrgUrl()).isEqualTo("https://dev.azure.com/acme");
    }

    @Test
    void reusedTicket_redirectsWithTicketInvalid() throws Exception {
        tickets.add("raw-1", "org-1", "user-1", OAuthLinkPurpose.GITHUB_PROFILE, null, Instant.now().plusSeconds(60));
        filter.doFilter(request("github", "raw-1"), new MockHttpServletResponse(), new MockFilterChain());

        MockHttpServletRequest second = request("github", "raw-1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(second, response, chain);

        assertInvalid(response, chain, second, "github");
    }

    @Test
    void expiredTicket_redirectsWithTicketInvalid() throws Exception {
        tickets.add("raw-old", "org-1", "user-1", OAuthLinkPurpose.GITHUB_PROFILE, null, Instant.now().minusSeconds(1));
        MockHttpServletRequest request = request("github", "raw-old");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertInvalid(response, chain, request, "github");
    }

    @Test
    void missingTicket_redirectsWithTicketInvalid_notABlank401() throws Exception {
        MockHttpServletRequest request = request("azure", null);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertInvalid(response, chain, request, "azure");
    }

    @Test
    void ticketOfAnotherPurpose_isRefusedAndStaysUnconsumed() throws Exception {
        tickets.add("raw-gh", "org-1", "user-1", OAuthLinkPurpose.GITHUB_PROFILE, null, Instant.now().plusSeconds(60));
        MockHttpServletRequest request = request("azure", "raw-gh");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertInvalid(response, chain, request, "azure");
        MockFilterChain okChain = new MockFilterChain();
        filter.doFilter(request("github", "raw-gh"), new MockHttpServletResponse(), okChain);
        assertThat(okChain.getRequest()).isNotNull();
    }

    @Test
    void otherPaths_areIgnored() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/projects");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    private void assertInvalid(MockHttpServletResponse response, MockFilterChain chain,
                               MockHttpServletRequest request, String provider) {
        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getRedirectedUrl()).isEqualTo(FRONT + "/oauth/retorno?provider=" + provider
                + "&status=error&code=OAUTH_TICKET_INVALID");
        assertThat(response.getHeader("Set-Cookie")).isNull();
        assertThat(request.getSession(false)).isNull();
    }

    private static MockHttpServletRequest request(String registrationId, String ticket) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET",
                "/api/v1/auth/oauth2/authorization/" + registrationId);
        if (ticket != null) request.setParameter("ticket", ticket);
        return request;
    }

    /** Mesma regra atomica do repositorio Mongo: nao consumido, nao vencido, mesmo proposito. */
    private static final class InMemoryTickets implements OAuthLinkTicketRepository {
        private final List<OAuthLinkTicket> stored = new ArrayList<>();
        private final List<String> consumed = new ArrayList<>();

        void add(String raw, String org, String user, OAuthLinkPurpose purpose, String azureUrl, Instant expiresAt) {
            stored.add(new OAuthLinkTicket("id-" + raw, org, user, OAuthLinkTicket.hash(raw), purpose, azureUrl,
                    expiresAt, null, Instant.now()));
        }

        @Override
        public OAuthLinkTicket save(OAuthLinkTicket ticket) {
            stored.add(ticket);
            return ticket;
        }

        @Override
        public synchronized Optional<OAuthLinkTicket> consume(String hash, OAuthLinkPurpose purpose, Instant now) {
            return stored.stream()
                    .filter(t -> t.getTicketHash().equals(hash) && t.getPurpose() == purpose
                            && t.getExpiresAt().isAfter(now) && !consumed.contains(hash))
                    .findFirst()
                    .map(t -> {
                        consumed.add(hash);
                        return t;
                    });
        }
    }
}
