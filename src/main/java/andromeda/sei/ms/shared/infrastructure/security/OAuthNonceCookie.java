package andromeda.sei.ms.shared.infrastructure.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseCookie;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Optional;

/** Cookie do nonce do fluxo OAuth (perfil-integracoes D1). */
public final class OAuthNonceCookie {

    public static final String NAME = "andromeda_oauth_nonce";
    public static final String PATH = "/api/v1/auth/oauth2";
    static final Duration MAX_AGE = Duration.ofMinutes(10);

    private OAuthNonceCookie() {}

    public static ResponseCookie issue(String nonce, boolean secure) {
        return base(nonce, secure).maxAge(MAX_AGE).build();
    }

    /** Apaga o cookie (mesmos atributos, Max-Age=0). */
    public static ResponseCookie clear(boolean secure) {
        return base("", secure).maxAge(Duration.ZERO).build();
    }

    /** O nonce enviado pelo navegador confere com o hash guardado na sessao? Ausente ou errado = false. */
    public static boolean matches(HttpServletRequest request, String expectedHash) {
        if (expectedHash == null) return false;
        Optional<String> nonce = read(request);
        if (nonce.isEmpty()) return false;
        return MessageDigest.isEqual(
                andromeda.sei.ms.feature.auth.domain.OAuthLinkTicket.hash(nonce.get()).getBytes(StandardCharsets.UTF_8),
                expectedHash.getBytes(StandardCharsets.UTF_8));
    }

    private static Optional<String> read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return Optional.empty();
        for (Cookie c : cookies) {
            if (NAME.equals(c.getName()) && c.getValue() != null && !c.getValue().isBlank()) {
                return Optional.of(c.getValue());
            }
        }
        return Optional.empty();
    }

    private static ResponseCookie.ResponseCookieBuilder base(String value, boolean secure) {
        return ResponseCookie.from(NAME, value).httpOnly(true).secure(secure).sameSite("Lax").path(PATH);
    }
}
