package andromeda.sei.ms.feature.organization.presentation;

import andromeda.sei.ms.feature.organization.application.dto.CreateOrganizationOutput;
import andromeda.sei.ms.feature.organization.application.dto.OnboardOrganizationInput;
import andromeda.sei.ms.feature.organization.application.dto.OnboardOrganizationOutput;
import andromeda.sei.ms.feature.organization.presentation.dto.OnboardOrganizationRequest;
import andromeda.sei.ms.feature.organization.presentation.dto.OnboardOrganizationResponse;
import andromeda.sei.ms.feature.organization.presentation.dto.OrganizationResponse;
import org.springframework.stereotype.Component;

@Component
public class OrganizationPresentationMapper {

    public OnboardOrganizationInput toInput(OnboardOrganizationRequest request) {
        return new OnboardOrganizationInput(request.name(), request.adminEmail(), request.adminName(), request.adminPassword());
    }

    public OnboardOrganizationResponse toResponse(OnboardOrganizationOutput output, String token) {
        return new OnboardOrganizationResponse(
                output.orgId(),
                output.orgName(),
                output.orgSlug(),
                output.adminEmail(),
                token
        );
    }

    public OrganizationResponse toResponse(CreateOrganizationOutput output) {
        return new OrganizationResponse(output.id(), output.name(), output.slug(), output.createdAt());
    }
}
