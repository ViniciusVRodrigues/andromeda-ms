package andromeda.sei.ms.feature.agent.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/** Generation, normalization and hashing of the pairing codes and of the agent tokens. */
public final class AgentCodes {

    /** No vowels and no Y: a code can never spell a word. */
    public static final String USER_CODE_ALPHABET = "BCDFGHJKLMNPQRSTVWXZ";
    public static final int USER_CODE_LENGTH = 8;

    public static final String DEVICE_CODE_PREFIX = "adc_";
    public static final String ACCESS_TOKEN_PREFIX = "adt_";
    public static final String REFRESH_TOKEN_PREFIX = "adr_";

    private static final SecureRandom RANDOM = new SecureRandom();

    private AgentCodes() {
    }

    /** Eight characters of the alphabet, unformatted ({@code BCDFGHJK}). */
    public static String newUserCode() {
        StringBuilder code = new StringBuilder(USER_CODE_LENGTH);
        for (int i = 0; i < USER_CODE_LENGTH; i++) {
            code.append(USER_CODE_ALPHABET.charAt(RANDOM.nextInt(USER_CODE_ALPHABET.length())));
        }
        return code.toString();
    }

    /** {@code BCDFGHJK} to {@code BCDF-GHJK}. */
    public static String formatUserCode(String normalized) {
        return normalized.substring(0, 4) + "-" + normalized.substring(4);
    }

    /**
     * What the person typed to the canonical form: ignores case, hyphens and spaces. Empty when the
     * result is not 8 characters of the alphabet (it can never match a stored code).
     */
    public static Optional<String> normalizeUserCode(String typed) {
        if (typed == null) {
            return Optional.empty();
        }
        StringBuilder out = new StringBuilder(USER_CODE_LENGTH);
        for (char c : typed.toCharArray()) {
            if (c == '-' || Character.isWhitespace(c)) {
                continue;
            }
            out.append(Character.toUpperCase(c));
        }
        if (out.length() != USER_CODE_LENGTH) {
            return Optional.empty();
        }
        for (int i = 0; i < out.length(); i++) {
            if (USER_CODE_ALPHABET.indexOf(out.charAt(i)) < 0) {
                return Optional.empty();
            }
        }
        return Optional.of(out.toString());
    }

    public static String newDeviceCode() {
        return DEVICE_CODE_PREFIX + randomToken();
    }

    public static String newAccessToken() {
        return ACCESS_TOKEN_PREFIX + randomToken();
    }

    public static String newRefreshToken() {
        return REFRESH_TOKEN_PREFIX + randomToken();
    }

    /** 32 random bytes, base64url without padding (43 characters). */
    private static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
