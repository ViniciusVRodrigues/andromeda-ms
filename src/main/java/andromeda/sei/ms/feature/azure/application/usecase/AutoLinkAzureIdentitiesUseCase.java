package andromeda.sei.ms.feature.azure.application.usecase;

import andromeda.sei.ms.feature.allocation.application.usecase.ReattributeAzureActivityUseCase;
import andromeda.sei.ms.feature.azure.application.AzureIdentityMatchService;
import andromeda.sei.ms.feature.azure.domain.AzureIdentityMatcher;
import andromeda.sei.ms.feature.azure.domain.AzureOrgUser;
import andromeda.sei.ms.feature.azure.domain.AzureOrgUserRepository;
import andromeda.sei.ms.feature.user.domain.User;
import andromeda.sei.ms.feature.user.domain.UserRepository;
import andromeda.sei.ms.feature.workitem.domain.WorkItemRepository;
import andromeda.sei.ms.shared.application.security.VcsIdentityLinked;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Links users without {@code azureId} to the single org identity that carries their email
 * (perfil-integracoes D11). The write is conditional on {@code azureId} still being null, so it never
 * overwrites an admin link and loses silently to a concurrent one. No permission event: an Azure
 * identity does not change permissions. Each written link publishes {@link VcsIdentityLinked}, so the
 * person's unowned VCS history is attributed in background.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutoLinkAzureIdentitiesUseCase {

    private final UserRepository userRepository;
    private final AzureOrgUserRepository azureOrgUserRepository;
    private final AzureIdentityMatchService matchService;
    private final WorkItemRepository workItemRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ReattributeAzureActivityUseCase reattributeActivity;

    /** All active users of the organization; returns how many were linked. */
    public int executeForOrganization(String orgId) {
        List<User> users = userRepository.findAllByOrganizationId(orgId).stream()
                .filter(u -> u.isActive() && u.getAzureId() == null)
                .toList();
        if (users.isEmpty()) return 0;
        List<AzureOrgUser> candidates = azureOrgUserRepository.findAllByOrganizationIdAndActiveTrue(orgId);
        Set<String> taken = new HashSet<>(matchService.takenAzureIds(orgId, null));

        int linked = 0;
        for (User user : users) {
            try {
                if (link(orgId, user, candidates, taken)) linked++;
            } catch (RuntimeException e) {
                log.warn("Azure auto-link failed for a user: org={} userId={}: {}", orgId, user.getId(),
                        e.getClass().getSimpleName());
            }
        }
        log.info("Azure auto-link completed: org={} linked={}", orgId, linked);
        return linked;
    }

    /** One user (account completion); returns whether a link was written. */
    public boolean executeForUser(String orgId, String userId) {
        User user = userRepository.findByIdAndOrganizationId(userId, orgId).orElse(null);
        if (user == null || !user.isActive() || user.getAzureId() != null) return false;
        List<AzureOrgUser> candidates = azureOrgUserRepository.findAllByOrganizationIdAndActiveTrue(orgId);
        return link(orgId, user, candidates, new HashSet<>(matchService.takenAzureIds(orgId, userId)));
    }

    private boolean link(String orgId, User user, List<AzureOrgUser> candidates, Set<String> taken) {
        AzureIdentityMatcher.Result result = matchService.evaluate(user, candidates, taken);
        if (result.status() != AzureIdentityMatcher.Status.MATCH) return false;

        AzureOrgUser identity = result.candidate();
        boolean written = userRepository.linkAutoEmailIfUnlinked(orgId, user.getId(), identity.getAzureId(),
                identity.getAzureEmail() != null ? identity.getAzureEmail() : identity.getPrincipalName(),
                Instant.now());
        if (!written) return false;

        taken.add(identity.getAzureId());
        workItemRepository.updateAssigneeUserIdByExternalIdOrEmail(orgId, identity.getAzureId(), user.getEmail(),
                user.getId());
        eventPublisher.publishEvent(new VcsIdentityLinked(orgId, user.getId()));
        reattributeActivity.execute(orgId, user.getId(), identity.getAzureId(), user.getEmail());
        return true;
    }
}
