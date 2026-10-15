package andromeda.sei.ms.feature.organization.presentation;

import andromeda.sei.ms.feature.organization.application.dto.CreateOrganizationOutput;
import andromeda.sei.ms.feature.organization.application.dto.OnboardOrganizationInput;
import andromeda.sei.ms.feature.organization.application.dto.OnboardOrganizationOutput;
import andromeda.sei.ms.feature.organization.presentation.dto.AiPriceEntryResponse;
import andromeda.sei.ms.feature.organization.presentation.dto.OnboardOrganizationRequest;
import andromeda.sei.ms.feature.organization.presentation.dto.OnboardOrganizationResponse;
import andromeda.sei.ms.feature.organization.presentation.dto.OrganizationResponse;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OrganizationPresentationMapper {

    public OnboardOrganizationInput toInput(OnboardOrganizationRequest request) {
        List<OnboardOrganizationInput.RoleData> roles = request.roles() == null ? List.of() :
                request.roles().stream()
                        .map(r -> new OnboardOrganizationInput.RoleData(r.name(), r.label(), r.permissionCodes()))
                        .toList();

        List<OnboardOrganizationInput.InviteData> invites = request.invites() == null ? List.of() :
                request.invites().stream()
                        .map(i -> new OnboardOrganizationInput.InviteData(i.email(), i.roleName(), i.seniority()))
                        .toList();

        return new OnboardOrganizationInput(
                new OnboardOrganizationInput.OrgData(request.org().name(), request.org().sector(), request.org().country()),
                new OnboardOrganizationInput.AdminData(request.admin().email(), request.admin().name(), request.admin().password()),
                new OnboardOrganizationInput.ProjectData(request.project().name(), request.project().description()),
                request.seniorities(),
                roles,
                invites,
                request.funnelId()
        );
    }

    public OnboardOrganizationResponse toResponse(OnboardOrganizationOutput output, String token) {
        return new OnboardOrganizationResponse(
                output.orgId(),
                output.orgName(),
                output.orgSlug(),
                output.adminEmail(),
                output.projectId(),
                output.projectName(),
                output.inviteCount(),
                token
        );
    }

    public OrganizationResponse toResponse(CreateOrganizationOutput output) {
        List<AiPriceEntryResponse> aiPrices = output.aiPrices() == null ? null :
                output.aiPrices().stream()
                        .map(e -> new AiPriceEntryResponse(e.getModel(), e.getPricePerKInput(), e.getPricePerKOutput()))
                        .toList();
        return new OrganizationResponse(output.id(), output.name(), output.slug(),
                output.sector(), output.country(), output.seniorities(), aiPrices,
                output.subscriptionCostPerUserUsd(), output.createdAt());
    }
}
