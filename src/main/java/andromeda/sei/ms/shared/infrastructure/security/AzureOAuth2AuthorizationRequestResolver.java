package andromeda.sei.ms.shared.infrastructure.security;

import andromeda.sei.ms.feature.azure.domain.AzureOrgUrl;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.crypto.keygen.Base64StringKeyGenerator;
import org.springframework.security.crypto.keygen.StringKeyGenerator;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

import java.util.Base64;

/**
 * Azure authorization request: keeps the Andromeda JWT in the session (existing flow) and, since
 * azure-repos-provider D7, carries the organization URL informed by the admin ({@code ?orgUrl=})
 * in the {@code state} ({@link AzureOAuthOrgUrlState}). The state is set through the resolver's
 * customizer, before the authorization URI is built, so the URI sent to Microsoft and the stored
 * request share it. A missing or malformed URL leaves the default state; the callback then refuses
 * to store the connection ({@code AZURE_ORG_URL_INVALID}).
 */
public class AzureOAuth2AuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {

    private static final String AZURE = "azure";
    /** The org URL of the request being resolved, handed to the customizer on the same thread. */
    private static final ThreadLocal<String> ORG_URL = new ThreadLocal<>();

    private final DefaultOAuth2AuthorizationRequestResolver defaultResolver;
    private final StringKeyGenerator stateGenerator = new Base64StringKeyGenerator(Base64.getUrlEncoder());

    public AzureOAuth2AuthorizationRequestResolver(ClientRegistrationRepository clientRegistrationRepository) {
        this.defaultResolver = new DefaultOAuth2AuthorizationRequestResolver(
                clientRegistrationRepository, "/api/v1/auth/oauth2/authorization");
        this.defaultResolver.setAuthorizationRequestCustomizer(builder -> {
            String orgUrl = ORG_URL.get();
            if (orgUrl != null) builder.state(AzureOAuthOrgUrlState.encode(stateGenerator.generateKey(), orgUrl));
        });
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        boolean azure = AZURE.equals(extractRegistrationId(request));
        OAuth2AuthorizationRequest result = withOrgUrl(request, azure, () -> defaultResolver.resolve(request));
        if (result != null && azure) saveOrgIdToSession(request);
        return result;
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
        boolean azure = AZURE.equals(clientRegistrationId);
        OAuth2AuthorizationRequest result = withOrgUrl(request, azure,
                () -> defaultResolver.resolve(request, clientRegistrationId));
        if (result != null && azure) saveOrgIdToSession(request);
        return result;
    }

    private static OAuth2AuthorizationRequest withOrgUrl(HttpServletRequest request, boolean azure,
                                                         java.util.function.Supplier<OAuth2AuthorizationRequest> resolve) {
        String orgUrl = request.getParameter(AzureOAuthOrgUrlState.ORG_URL_PARAM);
        if (!azure || !AzureOrgUrl.isValid(orgUrl)) return resolve.get();
        ORG_URL.set(AzureOrgUrl.normalize(orgUrl));
        try {
            return resolve.get();
        } finally {
            ORG_URL.remove();
        }
    }

    private void saveOrgIdToSession(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            request.getSession().setAttribute("ANDROMEDA_JWT", authHeader.substring(7));
        }
    }

    private String extractRegistrationId(HttpServletRequest request) {
        String path = request.getRequestURI();
        int lastSlash = path.lastIndexOf('/');
        return lastSlash >= 0 ? path.substring(lastSlash + 1) : null;
    }
}
