package andromeda.sei.ms.feature.organization.application.usecase;

import andromeda.sei.ms.feature.organization.application.dto.AllocationPolicyOutput;
import andromeda.sei.ms.feature.organization.domain.Organization;
import andromeda.sei.ms.feature.organization.domain.OrganizationRepository;
import andromeda.sei.ms.feature.organization.domain.exception.OrganizationNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GetAllocationPolicyUseCase {

    private final OrganizationRepository organizationRepository;

    public AllocationPolicyOutput execute(String organizationId) {
        return organizationRepository.findById(organizationId)
                .map(Organization::getAllocationPolicy)
                .map(AllocationPolicyOutput::from)
                .orElseThrow(() -> new OrganizationNotFoundException(organizationId));
    }
}
