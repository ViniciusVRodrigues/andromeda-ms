package andromeda.sei.ms.shared.infrastructure.security;

import org.springframework.web.util.UriComponentsBuilder;

/**
 * URL da pagina de retorno do app (perfil-integracoes D4): {@code /oauth/retorno?provider=&status=&code=}.
 * Lista fechada de provedores, status e codigos; nunca token, e-mail ou outro dado pessoal na URL.
 */
public final class OAuthReturnRedirect {

    public enum Provider {
        GITHUB, AZURE;

        public String id() { return name().toLowerCase(java.util.Locale.ROOT); }

        /** Id de registro do Spring ({@code github}/{@code azure}); desconhecido cai no GitHub. */
        public static Provider fromRegistrationId(String registrationId) {
            return "azure".equals(registrationId) ? AZURE : GITHUB;
        }
    }

    public enum Status {
        LINKED, CONNECTED, ERROR;

        String id() { return name().toLowerCase(java.util.Locale.ROOT); }
    }

    public enum Code {
        OAUTH_SESSION_MISMATCH,
        OAUTH_TICKET_INVALID,
        PROVIDER_ACCOUNT_ALREADY_LINKED,
        OAUTH_PROVIDER_ERROR,
        OAUTH_LINK_FAILED,
        AZURE_ORG_URL_INVALID
    }

    private OAuthReturnRedirect() {}

    public static String linked(String frontendUrl, Provider provider) {
        return build(frontendUrl, provider, Status.LINKED, null);
    }

    public static String error(String frontendUrl, Provider provider, Code code) {
        return build(frontendUrl, provider, Status.ERROR, code);
    }

    public static String build(String frontendUrl, Provider provider, Status status, Code code) {
        UriComponentsBuilder uri = UriComponentsBuilder.fromUriString(frontendUrl).path("/oauth/retorno")
                .queryParam("provider", provider.id())
                .queryParam("status", status.id());
        if (status == Status.ERROR) {
            if (code == null) throw new IllegalArgumentException("code is required for status=error");
            uri.queryParam("code", code.name());
        }
        return uri.build().toUriString();
    }
}
