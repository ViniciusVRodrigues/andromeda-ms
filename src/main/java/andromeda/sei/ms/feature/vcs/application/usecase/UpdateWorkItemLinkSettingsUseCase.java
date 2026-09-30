package andromeda.sei.ms.feature.vcs.application.usecase;

import andromeda.sei.ms.feature.organization.domain.Organization;
import andromeda.sei.ms.feature.organization.domain.OrganizationRepository;
import andromeda.sei.ms.feature.organization.domain.VcsSettings;
import andromeda.sei.ms.feature.organization.domain.WorkItemLinkConfig;
import andromeda.sei.ms.feature.organization.domain.exception.OrganizationNotFoundException;
import andromeda.sei.ms.feature.vcs.application.dto.WorkItemLinkSettingsInput;
import andromeda.sei.ms.feature.vcs.application.dto.WorkItemLinkSettingsOutput;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/** Stores the organization's pull request ↔ work item link settings in {@code vcsSettings.workItemLink}. */
@Component
@RequiredArgsConstructor
public class UpdateWorkItemLinkSettingsUseCase {

    private final OrganizationRepository organizationRepository;

    public WorkItemLinkSettingsOutput execute(WorkItemLinkSettingsInput input) {
        String orgId = TenantContextHolder.get();
        Organization organization = organizationRepository.findById(orgId)
                .orElseThrow(() -> new OrganizationNotFoundException(orgId));

        // Blank entries would widen every reference to a bare number — the ambiguity prefixes prevent.
        List<String> prefixes = input.prefixes() == null ? List.of()
                : input.prefixes().stream().filter(p -> p != null).map(String::trim).filter(p -> !p.isBlank()).toList();
        VcsSettings current = organization.getVcsSettings() == null ? VcsSettings.defaults() : organization.getVcsSettings();
        organization.setVcsSettings(current.withWorkItemLink(new WorkItemLinkConfig(input.enabled(), prefixes)));

        WorkItemLinkConfig stored = organizationRepository.save(organization).getVcsSettings().effectiveWorkItemLink();
        return new WorkItemLinkSettingsOutput(stored.isEnabled(), stored.getPrefixes());
    }
}
