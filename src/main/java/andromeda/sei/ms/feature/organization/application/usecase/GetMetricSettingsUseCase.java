package andromeda.sei.ms.feature.organization.application.usecase;

import andromeda.sei.ms.feature.organization.application.dto.MetricSettingsOutput;
import andromeda.sei.ms.feature.organization.domain.Organization;
import andromeda.sei.ms.feature.organization.domain.OrganizationRepository;
import andromeda.sei.ms.feature.organization.domain.exception.OrganizationNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GetMetricSettingsUseCase {

    private final OrganizationRepository organizationRepository;

    public MetricSettingsOutput execute(String organizationId) {
        return organizationRepository.findById(organizationId)
                .map(Organization::getMetricSettings)
                .map(MetricSettingsOutput::from)
                .orElseThrow(() -> new OrganizationNotFoundException(organizationId));
    }
}
