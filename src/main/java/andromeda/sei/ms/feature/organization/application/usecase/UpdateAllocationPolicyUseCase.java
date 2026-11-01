package andromeda.sei.ms.feature.organization.application.usecase;

import andromeda.sei.ms.feature.organization.application.dto.AllocationPolicyOutput;
import andromeda.sei.ms.feature.organization.domain.AllocationPolicy;
import andromeda.sei.ms.feature.organization.domain.Organization;
import andromeda.sei.ms.feature.organization.domain.OrganizationRepository;
import andromeda.sei.ms.feature.organization.domain.exception.OrganizationNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Replaces the allocation policy of the organization. Takes effect on the next allocation; nothing is recalculated. */
@Component
@RequiredArgsConstructor
public class UpdateAllocationPolicyUseCase {

    private final OrganizationRepository organizationRepository;

    public AllocationPolicyOutput execute(String organizationId, int outOfDeliveryCapPercent) {
        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new OrganizationNotFoundException(organizationId));
        organization.setAllocationPolicy(new AllocationPolicy(outOfDeliveryCapPercent));
        return AllocationPolicyOutput.from(organizationRepository.save(organization).getAllocationPolicy());
    }
}
