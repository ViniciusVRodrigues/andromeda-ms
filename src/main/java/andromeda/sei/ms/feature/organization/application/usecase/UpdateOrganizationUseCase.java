package andromeda.sei.ms.feature.organization.application.usecase;

import andromeda.sei.ms.feature.organization.application.dto.CreateOrganizationOutput;
import andromeda.sei.ms.feature.organization.domain.OrganizationRepository;
import andromeda.sei.ms.feature.organization.domain.exception.OrganizationNotFoundException;
import andromeda.sei.ms.feature.organization.domain.exception.OrganizationSenioritiesEmptyException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
public class UpdateOrganizationUseCase {

    private final OrganizationRepository organizationRepository;

    public CreateOrganizationOutput execute(String organizationId, List<String> seniorities) {
        if (seniorities == null || seniorities.isEmpty()) {
            throw new OrganizationSenioritiesEmptyException();
        }
        var org = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new OrganizationNotFoundException(organizationId));

        org.setSeniorities(seniorities);
        org.setUpdatedAt(Instant.now());

        return CreateOrganizationOutput.from(organizationRepository.save(org));
    }
}
