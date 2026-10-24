package andromeda.sei.ms.feature.azure.infrastructure.vcs;

import andromeda.sei.ms.feature.azure.application.port.AzureOrgUserCollector;
import andromeda.sei.ms.feature.azure.domain.AzureOrgUser;
import andromeda.sei.ms.feature.azure.domain.AzureOrgUserRepository;
import andromeda.sei.ms.feature.azure.domain.AzureOrgUserSource;
import andromeda.sei.ms.feature.user.domain.User;
import andromeda.sei.ms.feature.user.domain.UserRepository;
import andromeda.sei.ms.feature.vcs.application.port.VcsAuthorResolver;
import andromeda.sei.ms.feature.vcs.domain.VcsProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Azure DevOps identity → Andromeda user (design D4, azure-repos-integration "Identity resolution
 * through Azure DevOps identities"), in order:
 * <ol>
 *   <li>{@code User.azureId} equal to the identity id (normalized GUID — the same id Boards stores
 *       from {@code System.AssignedTo});</li>
 *   <li>the identity's email — the {@code uniqueName} of the payload, else the one cached in
 *       {@code azure_org_users} for that id — against {@code User.azureEmail}, then {@code User.email},
 *       within the organization, ignoring case.</li>
 * </ol>
 * Commits carry no identity id, only the Git author email, so they resolve by step 2 alone.
 * Nothing matching leaves the user id {@code null}; the caller keeps the display name.
 */
@Component
@RequiredArgsConstructor
public class AzureReposAuthorResolver implements VcsAuthorResolver {

    private final UserRepository userRepository;
    private final AzureOrgUserRepository azureOrgUserRepository;
    private final AzureOrgUserCollector orgUserCollector;
    private final Map<String, Map<String, AzureOrgUserCollector.ObservedIdentity>> observed =
            new ConcurrentHashMap<>();

    @Override
    public VcsProvider provider() {
        return VcsProvider.AZURE_REPOS;
    }

    @Override
    public void observe(String organizationId, String actorExternalId, String displayName, String actorEmail) {
        String id = AzureIdentityIds.normalize(actorExternalId);
        if (organizationId == null || id == null) return;
        observed.computeIfAbsent(organizationId, k -> new ConcurrentHashMap<>())
                .putIfAbsent(id, new AzureOrgUserCollector.ObservedIdentity(id, displayName, actorEmail,
                        AzureOrgUserSource.PR));
    }

    @Override
    public void flushObserved(String organizationId) {
        Map<String, AzureOrgUserCollector.ObservedIdentity> batch = observed.remove(organizationId);
        if (batch != null && !batch.isEmpty()) {
            orgUserCollector.collect(organizationId, new LinkedHashMap<>(batch).values());
        }
    }

    @Override
    public String resolveUserId(String organizationId, String actorExternalId) {
        return resolveUserId(organizationId, actorExternalId, null);
    }

    @Override
    public String resolveUserId(String organizationId, String actorExternalId, String actorEmail) {
        String id = AzureIdentityIds.normalize(actorExternalId);
        return byAzureId(organizationId, id, actorExternalId)
                .or(() -> byEmail(organizationId, emailOf(organizationId, id, actorEmail)))
                .orElse(null);
    }

    private Optional<String> byAzureId(String organizationId, String normalizedId, String rawId) {
        if (normalizedId == null) return Optional.empty();
        Optional<User> user = userRepository.findByOrganizationIdAndAzureId(organizationId, normalizedId);
        if (user.isEmpty() && rawId != null && !rawId.trim().equals(normalizedId)) {
            user = userRepository.findByOrganizationIdAndAzureId(organizationId, rawId.trim());
        }
        return user.map(User::getId);
    }

    private String emailOf(String organizationId, String normalizedId, String actorEmail) {
        if (isEmail(actorEmail)) return actorEmail.trim();
        if (normalizedId == null) return null;
        return azureOrgUserRepository.findByOrganizationIdAndAzureIdAndActiveTrue(organizationId, normalizedId)
                .map(AzureOrgUser::getAzureEmail)
                .filter(AzureReposAuthorResolver::isEmail)
                .orElse(null);
    }

    private Optional<String> byEmail(String organizationId, String email) {
        if (email == null) return Optional.empty();
        return first(userRepository.findAllByOrganizationIdAndAzureEmailIgnoringCase(organizationId, email))
                .or(() -> first(userRepository.findAllByOrganizationIdAndEmailIgnoringCase(organizationId, email)));
    }

    private static Optional<String> first(List<User> users) {
        return users == null || users.isEmpty() ? Optional.empty() : Optional.ofNullable(users.getFirst().getId());
    }

    /** Service accounts carry non-email unique names ({@code Build\...}, {@code vstfs:///...}). */
    private static boolean isEmail(String value) {
        return value != null && value.contains("@") && !value.isBlank();
    }
}
