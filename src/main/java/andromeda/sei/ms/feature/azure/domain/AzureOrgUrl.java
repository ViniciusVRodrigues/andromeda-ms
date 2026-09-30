package andromeda.sei.ms.feature.azure.domain;

import java.util.regex.Pattern;

/**
 * The accepted shapes of an Azure DevOps Services organization URL (azure-org-connection):
 * {@code https://dev.azure.com/{org}} or {@code https://{org}.visualstudio.com}, with an optional
 * trailing slash. Shared by the PAT form, the OAuth start and the URL fix endpoint.
 */
public final class AzureOrgUrl {

    public static final String EXPECTED_SHAPE =
            "https://dev.azure.com/{org} or https://{org}.visualstudio.com";

    private static final Pattern DEV_AZURE_COM_URL = Pattern.compile("^https://dev\\.azure\\.com/[^/\\s]+/?$");
    private static final Pattern VISUALSTUDIO_COM_URL = Pattern.compile("^https://[^/\\s.]+\\.visualstudio\\.com/?$");

    private AzureOrgUrl() {}

    public static boolean isValid(String url) {
        if (url == null || url.isBlank()) return false;
        return DEV_AZURE_COM_URL.matcher(url).matches() || VISUALSTUDIO_COM_URL.matcher(url).matches();
    }

    /** The URL without its trailing slash; callers validate first. */
    public static String normalize(String url) {
        String trimmed = url.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }
}
