package andromeda.sei.ms.feature.azure.application;

import andromeda.sei.ms.feature.azure.domain.AzureIdentityMatcher;
import andromeda.sei.ms.feature.azure.domain.AzureOrgUser;
import andromeda.sei.ms.feature.azure.domain.AzureOrgUserRepository;
import andromeda.sei.ms.feature.user.domain.User;
import andromeda.sei.ms.feature.user.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Read-only evaluation of a user against the persisted org directory, always scoped to the user's
 * organization. Reused by the auto-link use case and by the self-service state endpoint.
 */
@Service
@RequiredArgsConstructor
public class AzureIdentityMatchService {

    private final AzureOrgUserRepository azureOrgUserRepository;
    private final UserRepository userRepository;

    public AzureIdentityMatcher.Result evaluate(User user) {
        String orgId = user.getOrganizationId();
        List<AzureOrgUser> candidates = azureOrgUserRepository.findAllByOrganizationIdAndActiveTrue(orgId);
        return AzureIdentityMatcher.match(user.getEmail(), user.getAzureId() != null, candidates,
                takenAzureIds(orgId, user.getId()));
    }

    /** Evaluation with data already loaded, for batches (avoids re-reading per user). */
    public AzureIdentityMatcher.Result evaluate(User user, List<AzureOrgUser> candidates, Set<String> takenAzureIds) {
        return AzureIdentityMatcher.match(user.getEmail(), user.getAzureId() != null, candidates, takenAzureIds);
    }

    public Set<String> takenAzureIds(String orgId, String exceptUserId) {
        return userRepository.findAllByOrganizationId(orgId).stream()
                .filter(u -> u.getAzureId() != null && !u.getId().equals(exceptUserId))
                .map(User::getAzureId)
                .collect(Collectors.toSet());
    }
}
