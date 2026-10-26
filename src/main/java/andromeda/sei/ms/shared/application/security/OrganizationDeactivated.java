package andromeda.sei.ms.shared.application.security;

/** An organization was logically deleted: every cached permission snapshot must be dropped. */
public record OrganizationDeactivated(String organizationId) {}
