package andromeda.sei.ms.shared.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.crypto.keygen.Base64StringKeyGenerator;
import org.springframework.security.crypto.keygen.StringKeyGenerator;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

import java.util.Base64;
import java.util.function.Supplier;

/**
 * Resolver unico de GitHub e Azure (substitui os que liam o JWT do Authorization). Quem identifica a
 * pessoa e o {@link LinkBinding} que o {@link OAuthTicketAuthorizationFilter} gravou na sessao ao
 * consumir o ticket; sem vinculo na sessao nao ha pedido de autorizacao. No Azure, a URL da organizacao
 * do ticket vai no {@code state} ({@link AzureOAuthOrgUrlState}), nunca da query.
 */
public class TicketOAuth2AuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {

    private static final ThreadLocal<String> ORG_URL = new ThreadLocal<>();

    private final DefaultOAuth2AuthorizationRequestResolver defaultResolver;
    private final StringKeyGenerator stateGenerator = new Base64StringKeyGenerator(Base64.getUrlEncoder());

    public TicketOAuth2AuthorizationRequestResolver(ClientRegistrationRepository repository) {
        this.defaultResolver = new DefaultOAuth2AuthorizationRequestResolver(
                repository, "/api/v1/auth/oauth2/authorization");
        this.defaultResolver.setAuthorizationRequestCustomizer(builder -> {
            String orgUrl = ORG_URL.get();
            if (orgUrl != null) builder.state(AzureOAuthOrgUrlState.encode(stateGenerator.generateKey(), orgUrl));
        });
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        return withBinding(request, () -> defaultResolver.resolve(request));
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
        return withBinding(request, () -> defaultResolver.resolve(request, clientRegistrationId));
    }

    private static OAuth2AuthorizationRequest withBinding(HttpServletRequest request,
                                                          Supplier<OAuth2AuthorizationRequest> resolve) {
        HttpSession session = request.getSession(false);
        Object binding = session == null ? null : session.getAttribute(LinkBinding.SESSION_ATTRIBUTE);
        if (!(binding instanceof LinkBinding link)) return null;
        if (link.azureOrgUrl() == null) return resolve.get();
        ORG_URL.set(link.azureOrgUrl());
        try {
            return resolve.get();
        } finally {
            ORG_URL.remove();
        }
    }
}
