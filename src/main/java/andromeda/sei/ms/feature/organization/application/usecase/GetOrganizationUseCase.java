package andromeda.sei.ms.feature.organization.application.usecase;

import andromeda.sei.ms.feature.organization.application.dto.CreateOrganizationOutput;
import andromeda.sei.ms.feature.organization.domain.OrganizationRepository;
import andromeda.sei.ms.feature.organization.domain.exception.OrganizationNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GetOrganizationUseCase {

    private final OrganizationRepository organizationRepository;

    public CreateOrganizationOutput execute(String organizationId) {
        return organizationRepository.findById(organizationId)
                .map(CreateOrganizationOutput::from)
                .orElseThrow(() -> new OrganizationNotFoundException(organizationId));
    }
}
