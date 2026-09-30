package andromeda.sei.ms.shared.infrastructure.security;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

/**
 * Carries the Azure DevOps organization URL through the OAuth {@code state} (azure-repos-provider
 * D7): {@code <random state>.<base64url(orgUrl)>}. The whole value is what Spring stores with the
 * authorization request and matches on the callback, so the suffix cannot be swapped without
 * failing the state check; the callback handler then reads the URL back from the state.
 */
public final class AzureOAuthOrgUrlState {

    public static final String ORG_URL_PARAM = "orgUrl";
    private static final char SEPARATOR = '.';

    private AzureOAuthOrgUrlState() {}

    public static String encode(String state, String orgUrl) {
        return state + SEPARATOR + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(orgUrl.getBytes(StandardCharsets.UTF_8));
    }

    public static Optional<String> decode(String state) {
        if (state == null) return Optional.empty();
        int separator = state.lastIndexOf(SEPARATOR);
        if (separator < 0 || separator == state.length() - 1) return Optional.empty();
        try {
            return Optional.of(new String(Base64.getUrlDecoder().decode(state.substring(separator + 1)),
                    StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
