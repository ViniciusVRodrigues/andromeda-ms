package andromeda.sei.ms.shared.infrastructure.security;

import andromeda.sei.ms.feature.auth.domain.OAuthLinkPurpose;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration;
import org.springframework.boot.test.context.assertj.AssertableWebApplicationContext;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Task 3.1: com o application.yaml real, a URL de autorizacao leva o redirect_uri do dominio do app e
 * os escopos esperados; o Azure leva a URL da organizacao do ticket no state; sem vinculo na sessao
 * nao ha pedido (nao existe mais o caminho do ANDROMEDA_JWT).
 */
class TicketOAuth2AuthorizationRequestResolverTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withConfiguration(AutoConfigurations.of(OAuth2ClientAutoConfiguration.class))
            .withPropertyValues("app.frontend-url=https://andromeda.example");

    @Test
    void github_authorizationUri_hasTheAppRedirectUriAndIdentityScopes() {
        withResolver(resolver -> {
            OAuth2AuthorizationRequest result = resolver.resolve(request("github", binding(null)));

            assertThat(result).isNotNull();
            assertThat(result.getRedirectUri())
                    .isEqualTo("https://andromeda.example/api/v1/auth/oauth2/callback/github");
            assertThat(result.getScopes()).containsExactlyInAnyOrder("read:user", "user:email");
            assertThat(result.getAuthorizationRequestUri()).contains("redirect_uri=https://andromeda.example/api/v1/auth/oauth2/callback/github");
        });
    }

    @Test
    void azure_authorizationUri_hasTheAppRedirectUri_andTheTicketOrgUrlInTheState() {
        withResolver(resolver -> {
            MockHttpServletRequest request = request("azure", binding("https://dev.azure.com/acme"));
            request.setParameter("orgUrl", "https://dev.azure.com/evil");

            OAuth2AuthorizationRequest result = resolver.resolve(request);

            assertThat(result.getRedirectUri())
                    .isEqualTo("https://andromeda.example/api/v1/auth/oauth2/callback/azure");
            assertThat(result.getScopes()).contains("offline_access");
            assertThat(AzureOAuthOrgUrlState.decode(result.getState())).contains("https://dev.azure.com/acme");
        });
    }

    @Test
    void withoutABindingInTheSession_thereIsNoAuthorizationRequest() {
        withResolver(resolver -> {
            MockHttpServletRequest request = new MockHttpServletRequest("GET",
                    "/api/v1/auth/oauth2/authorization/github");
            request.setServletPath("/api/v1/auth/oauth2/authorization/github");
            request.addHeader("Authorization", "Bearer some.jwt.token");

            assertThat(resolver.resolve(request)).isNull();
            assertThat(request.getSession(false)).isNull();
        });
    }

    private void withResolver(Consumer<TicketOAuth2AuthorizationRequestResolver> body) {
        runner.run((AssertableWebApplicationContext context) -> {
            assertThat(context).hasNotFailed();
            body.accept(new TicketOAuth2AuthorizationRequestResolver(context.getBean(ClientRegistrationRepository.class)));
        });
    }

    private static LinkBinding binding(String azureOrgUrl) {
        return new LinkBinding("org-1", "user-1",
                azureOrgUrl == null ? OAuthLinkPurpose.GITHUB_PROFILE : OAuthLinkPurpose.AZURE_ORG_CONNECT,
                azureOrgUrl, "hash");
    }

    private static MockHttpServletRequest request(String registrationId, LinkBinding binding) {
        String path = "/api/v1/auth/oauth2/authorization/" + registrationId;
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setServletPath(path);
        request.getSession(true).setAttribute(LinkBinding.SESSION_ATTRIBUTE, binding);
        return request;
    }
}
