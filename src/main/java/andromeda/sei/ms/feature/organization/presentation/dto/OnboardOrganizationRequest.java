package andromeda.sei.ms.feature.organization.presentation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record OnboardOrganizationRequest(
        @Valid @NotNull OrgData org,
        @Valid @NotNull AdminData admin,
        @Valid @NotNull ProjectData project,
        @NotEmpty List<@NotBlank String> seniorities,
        List<@Valid RoleData> roles,
        List<@Valid InviteData> invites,
        /* optional; a malformed value is ignored by LinkFunnelUseCase, never an error */
        String funnelId
) {
    public record OrgData(
            @NotBlank String name,
            @NotBlank String sector,
            @NotBlank String country
    ) {}

    public record AdminData(
            @NotBlank @Email String email,
            @NotBlank String name,
            @NotBlank String password
    ) {}

    public record ProjectData(
            @NotBlank String name,
            @NotBlank String description
    ) {}

    public record RoleData(
            @NotBlank String name,
            String label,
            @NotEmpty List<@NotBlank String> permissionCodes
    ) {}

    public record InviteData(
            @NotBlank @Email String email,
            @NotBlank String roleName,
            String seniority
    ) {}
}
