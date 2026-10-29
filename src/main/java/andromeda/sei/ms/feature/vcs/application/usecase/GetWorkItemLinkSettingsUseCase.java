package andromeda.sei.ms.feature.vcs.application.usecase;

import andromeda.sei.ms.feature.organization.domain.Organization;
import andromeda.sei.ms.feature.organization.domain.OrganizationRepository;
import andromeda.sei.ms.feature.organization.domain.WorkItemPatterns;
import andromeda.sei.ms.feature.organization.domain.exception.OrganizationNotFoundException;
import andromeda.sei.ms.feature.vcs.application.dto.WorkItemLinkSettingsOutput;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The organization's pull request ↔ work item link settings in the legacy {@code {enabled, prefixes}}
 * shape, read from {@code workItemPatterns} (agente-endpoint-v2; the AB_HASH and PREFIX patterns). An organization setting: no VCS connection is required to read it.
 */
@Component
@RequiredArgsConstructor
public class GetWorkItemLinkSettingsUseCase {

    private final OrganizationRepository organizationRepository;

    public WorkItemLinkSettingsOutput execute() {
        String orgId = TenantContextHolder.get();
        Organization organization = organizationRepository.findById(orgId)
                .orElseThrow(() -> new OrganizationNotFoundException(orgId));
        WorkItemPatterns patterns = organization.getWorkItemPatterns();
        return new WorkItemLinkSettingsOutput(patterns.enabled(), patterns.legacyPrefixes());
    }
}
