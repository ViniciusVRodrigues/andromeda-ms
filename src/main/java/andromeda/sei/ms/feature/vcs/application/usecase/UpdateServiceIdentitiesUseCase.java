package andromeda.sei.ms.feature.vcs.application.usecase;

import andromeda.sei.ms.feature.organization.domain.Organization;
import andromeda.sei.ms.feature.organization.domain.OrganizationRepository;
import andromeda.sei.ms.feature.organization.domain.VcsSettings;
import andromeda.sei.ms.feature.organization.domain.exception.OrganizationNotFoundException;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Replaces the organization's service identity list (azure-repos-provider D5). Entries are
 * trimmed and de-duplicated; the work item link settings are preserved. Takes effect on the next
 * sync of each pull request.
 */
@Component
@RequiredArgsConstructor
public class UpdateServiceIdentitiesUseCase {

    private final OrganizationRepository organizationRepository;

    public List<String> execute(List<String> identities) {
        String orgId = TenantContextHolder.get();
        Organization organization = organizationRepository.findById(orgId)
                .orElseThrow(() -> new OrganizationNotFoundException(orgId));
        VcsSettings current = organization.getVcsSettings() == null ? VcsSettings.defaults() : organization.getVcsSettings();
        organization.setVcsSettings(current.withServiceIdentities(ServiceIdentitySettings.normalize(identities)));
        return organizationRepository.save(organization).getVcsSettings().serviceIdentities();
    }
}
