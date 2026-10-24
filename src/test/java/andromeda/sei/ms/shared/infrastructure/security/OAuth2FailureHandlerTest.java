package andromeda.sei.ms.shared.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class OAuth2FailureHandlerTest {

    private static final String FRONT = "http://localhost:5173";

    private final OAuth2FailureHandler handler = new OAuth2FailureHandler(
            new OAuthFlowCompletion(mock(OAuth2AuthorizedClientService.class), FRONT, true));

    @ParameterizedTest
    @ValueSource(strings = {"github", "azure"})
    void providerError_redirectsToTheAppWithProviderErrorCode_notA401(String provider) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/oauth2/callback/" + provider);
        request.getSession(true);
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(request, response,
                new OAuth2AuthenticationException(new OAuth2Error("access_denied")));

        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getRedirectedUrl()).isEqualTo(FRONT + "/oauth/retorno?provider=" + provider
                + "&status=error&code=OAUTH_PROVIDER_ERROR");
        assertThat(request.getSession(false)).isNull();
        assertThat(response.getHeader("Set-Cookie")).contains("Max-Age=0");
    }

    @Test
    void failureWithoutSession_stillRedirects() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/oauth2/callback/github");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(request, response,
                new OAuth2AuthenticationException(new OAuth2Error("invalid_state")));

        assertThat(response.getRedirectedUrl()).contains("code=OAUTH_PROVIDER_ERROR");
    }
}
