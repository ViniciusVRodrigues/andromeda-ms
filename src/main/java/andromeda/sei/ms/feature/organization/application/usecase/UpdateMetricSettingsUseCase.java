package andromeda.sei.ms.feature.organization.application.usecase;

import andromeda.sei.ms.feature.organization.application.dto.MetricSettingsOutput;
import andromeda.sei.ms.feature.organization.application.dto.UpdateMetricSettingsInput;
import andromeda.sei.ms.feature.organization.domain.MetricSettings;
import andromeda.sei.ms.feature.organization.domain.Organization;
import andromeda.sei.ms.feature.organization.domain.OrganizationRepository;
import andromeda.sei.ms.feature.organization.domain.RemediationRules;
import andromeda.sei.ms.feature.organization.domain.exception.MetricSettingsInvalidException;
import andromeda.sei.ms.feature.organization.domain.exception.OrganizationNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Replaces the organization's remediation rules. Takes effect on the next read: delivery metrics
 * are computed on request, so nothing has to be recalculated.
 *
 * <p>Prefixes are trimmed and de-duplicated. A blank prefix is rejected because it would match
 * every branch and turn every merge into a failure.
 */
@Component
@RequiredArgsConstructor
public class UpdateMetricSettingsUseCase {

    private final OrganizationRepository organizationRepository;

    public MetricSettingsOutput execute(String organizationId, UpdateMetricSettingsInput input) {
        if (!RemediationRules.arePrefixesValid(input.remediationBranchPrefixes())) {
            throw new MetricSettingsInvalidException("remediation branch prefixes must not be blank");
        }
        List<String> prefixes = input.remediationBranchPrefixes().stream().map(String::trim).distinct().toList();

        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new OrganizationNotFoundException(organizationId));
        organization.setMetricSettings(new MetricSettings(new RemediationRules(
                prefixes, input.bugLinkedCountsAsRemediation(), input.revertCountsAsRemediation())));

        return MetricSettingsOutput.from(organizationRepository.save(organization).getMetricSettings());
    }
}
