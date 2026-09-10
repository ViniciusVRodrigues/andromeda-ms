package andromeda.sei.ms.feature.auth.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Geracao e verificacao do token de redefinicao.
 *
 * O token vai para o e-mail em claro e para o banco como hash. Comparar pelo
 * hash tambem resolve o tempo constante de graca: a consulta e por igualdade
 * de hash, nunca por comparacao de string do segredo.
 */
public final class PasswordResetTokens {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;

    private PasswordResetTokens() {}

    public static String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    /** Comparacao em tempo constante, para checagens fora do lookup por hash. */
    public static boolean matches(String token, String expectedHash) {
        if (token == null || expectedHash == null) return false;
        return MessageDigest.isEqual(
                hash(token).getBytes(StandardCharsets.UTF_8),
                expectedHash.getBytes(StandardCharsets.UTF_8));
    }
}
