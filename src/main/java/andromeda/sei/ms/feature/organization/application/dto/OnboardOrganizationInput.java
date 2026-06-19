package andromeda.sei.ms.feature.organization.application.dto;

import java.util.List;

public record OnboardOrganizationInput(
        OrgData org,
        AdminData admin,
        ProjectData project,
        List<String> seniorities,
        List<RoleData> roles,
        List<InviteData> invites
) {
    public record OrgData(String name, String sector, String country) {}
    public record AdminData(String email, String name, String password) {}
    public record ProjectData(String name, String description) {}
    public record RoleData(String name, String label, List<String> permissionCodes) {}
    public record InviteData(String email, String roleName, String seniority) {}
}
