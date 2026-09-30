package andromeda.sei.ms.feature.azure.infrastructure.vcs;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Canonical form of an Azure DevOps identity id (design D4): lowercase, hyphenated GUID. Pull
 * request authors and comment authors come hyphenated; thread properties such as
 * {@code CodeReviewReviewersUpdatedAddedTfId} come as 32 hex digits. Anything else is returned
 * trimmed and unchanged.
 */
public final class AzureIdentityIds {

    private static final Pattern BARE_GUID = Pattern.compile("^[0-9a-fA-F]{32}$");
    private static final Pattern HYPHENATED_GUID =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private AzureIdentityIds() {}

    public static String normalize(String id) {
        if (id == null) return null;
        String trimmed = id.trim();
        if (trimmed.isEmpty()) return null;
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) trimmed = trimmed.substring(1, trimmed.length() - 1);
        if (HYPHENATED_GUID.matcher(trimmed).matches()) return trimmed.toLowerCase(Locale.ROOT);
        if (BARE_GUID.matcher(trimmed).matches()) {
            String hex = trimmed.toLowerCase(Locale.ROOT);
            return hex.substring(0, 8) + "-" + hex.substring(8, 12) + "-" + hex.substring(12, 16) + "-"
                    + hex.substring(16, 20) + "-" + hex.substring(20);
        }
        return trimmed;
    }
}
