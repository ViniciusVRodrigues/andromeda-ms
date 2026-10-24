package andromeda.sei.ms.shared.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** Erro no callback do provedor (usuario negou, state invalido, troca de codigo falhou): volta ao app. */
@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2FailureHandler implements AuthenticationFailureHandler {

    private final OAuthFlowCompletion completion;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        String path = request.getRequestURI();
        OAuthReturnRedirect.Provider provider = OAuthReturnRedirect.Provider
                .fromRegistrationId(path.substring(path.lastIndexOf('/') + 1));
        String errorCode = exception instanceof OAuth2AuthenticationException oauth
                ? oauth.getError().getErrorCode() : "n/a";
        log.warn("OAuth callback failed: provider={} exceptionType={} errorCode={}",
                provider.id(), exception.getClass().getSimpleName(), errorCode);
        completion.error(request, response, provider, OAuthReturnRedirect.Code.OAUTH_PROVIDER_ERROR, null);
    }
}
