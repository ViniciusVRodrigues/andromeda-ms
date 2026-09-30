package andromeda.sei.ms.feature.vcs.application.usecase;

import andromeda.sei.ms.feature.organization.domain.Organization;
import andromeda.sei.ms.feature.organization.domain.OrganizationRepository;
import andromeda.sei.ms.feature.organization.domain.WorkItemLinkConfig;
import andromeda.sei.ms.feature.organization.domain.exception.OrganizationNotFoundException;
import andromeda.sei.ms.feature.vcs.application.dto.WorkItemLinkSettingsOutput;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The organization's pull request ↔ work item link settings (vcs-work-item-linking), falling back
 * to the defaults. An organization setting: no VCS connection is required to read it.
 */
@Component
@RequiredArgsConstructor
public class GetWorkItemLinkSettingsUseCase {

    private final OrganizationRepository organizationRepository;

    public WorkItemLinkSettingsOutput execute() {
        String orgId = TenantContextHolder.get();
        Organization organization = organizationRepository.findById(orgId)
                .orElseThrow(() -> new OrganizationNotFoundException(orgId));
        WorkItemLinkConfig config = organization.getVcsSettings().effectiveWorkItemLink();
        return new WorkItemLinkSettingsOutput(config.isEnabled(), config.getPrefixes());
    }
}
