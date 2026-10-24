package andromeda.sei.ms.shared.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Fim de todo caminho do callback OAuth, sucesso ou erro (perfil-integracoes D3/D4): remove o
 * authorized client, invalida a sessao do fluxo, apaga o cookie do nonce e redireciona para a pagina de
 * retorno do app. Nunca responde 401 nem corpo vazio no popup.
 */
@Slf4j
@Component
public class OAuthFlowCompletion {

    private final OAuth2AuthorizedClientService authorizedClientService;
    private final String frontendUrl;
    private final boolean secureCookie;

    public OAuthFlowCompletion(OAuth2AuthorizedClientService authorizedClientService,
                               @Value("${app.frontend-url}") String frontendUrl,
                               @Value("${app.oauth.cookie-secure:true}") boolean secureCookie) {
        this.authorizedClientService = authorizedClientService;
        this.frontendUrl = frontendUrl;
        this.secureCookie = secureCookie;
    }

    /** @param principalName nome do principal OAuth, ou null quando a autenticacao nao chegou a existir */
    public void finish(HttpServletRequest request, HttpServletResponse response,
                       OAuthReturnRedirect.Provider provider, OAuthReturnRedirect.Status status,
                       OAuthReturnRedirect.Code code, String principalName) throws IOException {
        if (principalName != null) {
            try {
                authorizedClientService.removeAuthorizedClient(provider.id(), principalName);
            } catch (RuntimeException e) {
                log.warn("Failed to remove OAuth authorized client: provider={} exceptionType={}",
                        provider.id(), e.getClass().getSimpleName());
            }
        }
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        response.addHeader(HttpHeaders.SET_COOKIE, OAuthNonceCookie.clear(secureCookie).toString());
        response.sendRedirect(OAuthReturnRedirect.build(frontendUrl, provider, status, code));
    }

    public void error(HttpServletRequest request, HttpServletResponse response,
                      OAuthReturnRedirect.Provider provider, OAuthReturnRedirect.Code code,
                      String principalName) throws IOException {
        finish(request, response, provider, OAuthReturnRedirect.Status.ERROR, code, principalName);
    }
}
