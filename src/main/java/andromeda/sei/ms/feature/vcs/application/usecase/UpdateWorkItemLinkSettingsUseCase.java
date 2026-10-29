package andromeda.sei.ms.feature.vcs.application.usecase;

import andromeda.sei.ms.feature.organization.domain.Organization;
import andromeda.sei.ms.feature.organization.domain.OrganizationRepository;
import andromeda.sei.ms.feature.organization.domain.WorkItemPatterns;
import andromeda.sei.ms.feature.organization.domain.exception.OrganizationNotFoundException;
import andromeda.sei.ms.feature.vcs.application.dto.WorkItemLinkSettingsInput;
import andromeda.sei.ms.feature.vcs.application.dto.WorkItemLinkSettingsOutput;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/** Stores the organization's pull request ↔ work item link settings in {@code workItemPatterns} (legacy prefixes route). */
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
        List<WorkItemPatterns.Entry> entries = prefixes.stream().map(WorkItemPatterns::fromPrefix).toList();
        WorkItemPatterns current = organization.getWorkItemPatterns();
        // Replaces every pattern by literal prefixes (mode CUSTOM). vcsSettings.workItemLink is not touched.
        organization.setWorkItemPatterns(new WorkItemPatterns(input.enabled(), WorkItemPatterns.Mode.CUSTOM,
                entries, current.confirmedBy(), current.confirmedAt(), null, Instant.now(), current.detection()));

        WorkItemPatterns stored = organizationRepository.save(organization).getWorkItemPatterns();
        return new WorkItemLinkSettingsOutput(stored.enabled(), stored.legacyPrefixes());
    }
}
