package andromeda.sei.ms.feature.organization.application.usecase;

import andromeda.sei.ms.feature.organization.application.dto.CreateOrganizationInput;
import andromeda.sei.ms.feature.organization.application.dto.CreateOrganizationOutput;
import andromeda.sei.ms.feature.organization.domain.Organization;
import andromeda.sei.ms.feature.organization.domain.OrganizationRepository;
import andromeda.sei.ms.feature.organization.domain.exception.OrganizationSlugConflictException;
import andromeda.sei.ms.shared.util.SlugUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class CreateOrganizationUseCase {

    private final OrganizationRepository repository;

    public CreateOrganizationOutput execute(CreateOrganizationInput input) {
        var slug = SlugUtils.toSlug(input.name());

        if (repository.existsBySlug(slug)) {
            throw new OrganizationSlugConflictException(slug);
        }

        var now = Instant.now();
        var organization = new Organization(null, input.name(), slug, null, null, null, null, null, true, null, now, now);

        var saved = repository.save(organization);
        return CreateOrganizationOutput.from(saved);
    }
}
