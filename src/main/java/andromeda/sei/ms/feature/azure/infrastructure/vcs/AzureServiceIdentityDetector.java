package andromeda.sei.ms.feature.azure.infrastructure.vcs;

import java.util.Collection;
import java.util.Locale;
import java.util.Objects;

/**
 * Service-account heuristics for Azure DevOps identities (design D5, a documented accepted risk):
 * <ul>
 *   <li>containers (groups, the collection service account that authors system comments);</li>
 *   <li>{@code uniqueName} starting with {@code Build\} (build service accounts);</li>
 *   <li>display names containing "Build Service" or "Project Collection";</li>
 *   <li>any id, unique name or display name listed in the organization's
 *       {@code vcsSettings.serviceIdentities}.</li>
 * </ul>
 */
public final class AzureServiceIdentityDetector {

    private static final String BUILD_UNIQUE_NAME_PREFIX = "build\\";
    private static final String BUILD_SERVICE = "build service";
    private static final String PROJECT_COLLECTION = "project collection";

    private AzureServiceIdentityDetector() {}

    public static boolean isServiceIdentity(String id, String uniqueName, String displayName, boolean container,
                                            Collection<String> organizationList) {
        if (container) return true;
        String unique = lower(uniqueName);
        String display = lower(displayName);
        if (unique != null && unique.startsWith(BUILD_UNIQUE_NAME_PREFIX)) return true;
        if (display != null && (display.contains(BUILD_SERVICE) || display.contains(PROJECT_COLLECTION))) return true;
        return listed(organizationList, AzureIdentityIds.normalize(id), unique, display);
    }

    private static boolean listed(Collection<String> organizationList, String id, String unique, String display) {
        if (organizationList == null || organizationList.isEmpty()) return false;
        return organizationList.stream().filter(Objects::nonNull).map(String::trim).anyMatch(entry -> {
            String normalized = lower(AzureIdentityIds.normalize(entry));
            return normalized != null && (normalized.equals(id) || normalized.equals(unique) || normalized.equals(display));
        });
    }

    private static String lower(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }
}
