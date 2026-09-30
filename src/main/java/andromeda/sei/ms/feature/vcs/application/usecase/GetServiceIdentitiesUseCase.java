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
 * The organization's own list of provider service identities (azure-repos-provider D5): review
 * signals from them are flagged as bots, on top of the built-in heuristics. API only, no UI.
 */
@Component
@RequiredArgsConstructor
public class GetServiceIdentitiesUseCase {

    private final OrganizationRepository organizationRepository;

    public List<String> execute() {
        String orgId = TenantContextHolder.get();
        Organization organization = organizationRepository.findById(orgId)
                .orElseThrow(() -> new OrganizationNotFoundException(orgId));
        VcsSettings settings = organization.getVcsSettings();
        return settings == null ? List.of() : settings.serviceIdentities();
    }
}
