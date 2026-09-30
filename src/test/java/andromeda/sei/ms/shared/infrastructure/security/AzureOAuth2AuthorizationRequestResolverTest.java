package andromeda.sei.ms.shared.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Task 1.2: the organization URL rides in the OAuth {@code state}, and the URI sent to Microsoft
 * carries that same state (otherwise the callback would fail Spring's state check).
 */
class AzureOAuth2AuthorizationRequestResolverTest {

    private static final String PATH = "/api/v1/auth/oauth2/authorization/azure";

    private final AzureOAuth2AuthorizationRequestResolver resolver = new AzureOAuth2AuthorizationRequestResolver(
            new InMemoryClientRegistrationRepository(ClientRegistration.withRegistrationId("azure")
                    .clientId("client").clientSecret("secret")
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .redirectUri("{baseUrl}/login/oauth2/code/azure")
                    .scope("offline_access")
                    .authorizationUri("https://login.microsoftonline.com/common/oauth2/v2.0/authorize")
                    .tokenUri("https://login.microsoftonline.com/common/oauth2/v2.0/token")
                    .build()));

    @Test
    void shouldCarryTheOrganizationUrlInTheStateOfTheAuthorizationUri() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", PATH);
        request.setServletPath(PATH);
        request.setParameter("orgUrl", "https://dev.azure.com/acme/");
        request.addHeader("Authorization", "Bearer jwt-1");

        OAuth2AuthorizationRequest result = resolver.resolve(request);

        assertThat(result).isNotNull();
        assertThat(AzureOAuthOrgUrlState.decode(result.getState())).contains("https://dev.azure.com/acme");
        String uriState = UriComponentsBuilder.fromUriString(result.getAuthorizationRequestUri()).build()
                .getQueryParams().getFirst("state");
        assertThat(URLDecoder.decode(uriState, StandardCharsets.UTF_8)).isEqualTo(result.getState());
        assertThat(request.getSession().getAttribute("ANDROMEDA_JWT")).isEqualTo("jwt-1");
    }

    @Test
    void shouldKeepTheDefaultStateWithoutAValidUrl() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", PATH);
        request.setServletPath(PATH);
        request.setParameter("orgUrl", "https://login.microsoftonline.com/tenant");

        OAuth2AuthorizationRequest result = resolver.resolve(request);

        assertThat(result).isNotNull();
        assertThat(AzureOAuthOrgUrlState.decode(result.getState())).isEmpty();
    }

    @Test
    void stateCodecShouldRoundTripAndRejectGarbage() {
        String state = AzureOAuthOrgUrlState.encode("abc_-=", "https://acme.visualstudio.com");

        assertThat(AzureOAuthOrgUrlState.decode(state)).contains("https://acme.visualstudio.com");
        assertThat(AzureOAuthOrgUrlState.decode("no-separator")).isEmpty();
        assertThat(AzureOAuthOrgUrlState.decode("x.")).isEmpty();
        assertThat(AzureOAuthOrgUrlState.decode("x.***")).isEmpty();
        assertThat(AzureOAuthOrgUrlState.decode(null)).isEmpty();
    }
}
