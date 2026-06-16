package andromeda.sei.ms.feature.organization.application.usecase;

import andromeda.sei.ms.feature.organization.application.dto.CreateOrganizationOutput;
import andromeda.sei.ms.feature.organization.application.dto.UpdateAiPricesInput;
import andromeda.sei.ms.feature.organization.domain.AiPriceEntry;
import andromeda.sei.ms.feature.organization.domain.OrganizationRepository;
import andromeda.sei.ms.feature.organization.domain.exception.OrganizationNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UpdateAiPricesUseCase {

    private final OrganizationRepository organizationRepository;

    public CreateOrganizationOutput execute(String orgId, UpdateAiPricesInput input) {
        var org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new OrganizationNotFoundException(orgId));

        List<AiPriceEntry> entries = input.entries() == null ? List.of() :
                input.entries().stream()
                        .map(e -> new AiPriceEntry(e.model(), e.pricePerKInput(), e.pricePerKOutput()))
                        .toList();

        org.setAiPrices(entries);
        org.setUpdatedAt(Instant.now());

        return CreateOrganizationOutput.from(organizationRepository.save(org));
    }
}
